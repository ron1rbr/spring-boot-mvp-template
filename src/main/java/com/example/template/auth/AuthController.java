package com.example.template.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.auth.dto.AuthResponse;
import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.auth.exception.InvalidRefreshTokenException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh and logout")
public class AuthController {

  private static final String SET_COOKIE_DESCRIPTION = "refresh_token cookie (httpOnly, Secure, SameSite=Strict, path /api/auth)";

  private final AuthService authService;
  private final RefreshTokenCookieFactory cookieFactory;

  @Operation(summary = "Register a new account", description = "Creates the account and signs the user in: returns an access token in the body and sets the refresh token cookie.")
  @SecurityRequirements
  @ApiResponse(responseCode = "201", description = "Account created", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_DESCRIPTION))
  @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.AuthResult result = authService.register(request);
    return withRefreshCookie(HttpStatus.CREATED, result);
  }

  @Operation(summary = "Log in with email and password")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "Authenticated", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_DESCRIPTION))
  @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthResult result = authService.login(request);
    return withRefreshCookie(HttpStatus.OK, result);
  }

  /**
   * Deliberately reads the token via @CookieValue, never from a JSON
   * body. The refresh token's only valid transport is the httpOnly
   * cookie set by register/login. The parameter is hidden from the
   * OpenAPI contract: browsers (and Swagger UI) send the cookie on
   * their own, and listing it as a parameter would suggest it can be
   * typed in by hand.
   */
  @Operation(summary = "Rotate the refresh token and get a new access token", description = "Reads the refresh token from the httpOnly cookie. The presented token is retired "
      + "and a new one is set in the cookie. Presenting an already-rotated token revokes every "
      + " session of the account.")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "Token rotated", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_DESCRIPTION))
  @ApiResponse(responseCode = "401", description = "Cookie is missing, token invalid/expired, or reuse detected (errorCode tells which)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException("No refresh token cookie present");
    }
    AuthService.AuthResult result = authService.refresh(refreshToken);
    return withRefreshCookie(HttpStatus.OK, result);
  }

  @Operation(summary = "Log out of the current device", description = "Revokes only the refresh token in this request's cookie and clears the cookie. "
      + "Needs no access token, so an expired session can still sign out.")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "Signed out; cookie cleared")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
    authService.logoutCurrentDevice(refreshToken);
    ResponseCookie expired = cookieFactory.clear();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
  }

  /**
   * Authenticated via the access token like any other protected
   * endpoint (see SecurityConfig, /api/auth/logout-all is NOT in the
   * permitAll list), not via the refresh cookie. This keeps "who is
   * logging out" tied to the same identity mechanism as the rest of
   * the API, and revokes every refresh token for that user, not just
   * the one in this browser's cookie.
   */
  @Operation(summary = "Log out of every device", description = "Revokes all refresh tokens of the authenticated user.")
  @ApiResponse(responseCode = "204", description = "All sessions revoked; cookie cleared")
  @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAllDevices(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal) {
    authService.logoutAllDevices(principal.getId());
    ResponseCookie expired = cookieFactory.clear();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
  }

  private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.AuthResult result) {
    ResponseCookie cookie = cookieFactory.build(result.rawRefreshToken());
    return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
  }

}
