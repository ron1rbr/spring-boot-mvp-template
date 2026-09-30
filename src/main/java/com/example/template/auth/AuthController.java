package com.example.template.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenCookieFactory cookieFactory;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.AuthResult result = authService.register(request);
    return withRefreshCookie(HttpStatus.CREATED, result);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthResult result = authService.login(request);
    return withRefreshCookie(HttpStatus.OK, result);
  }

  /**
   * Deliberately reads the token via @CookieValue, never from a JSON
   * body, the refresh token's only valid transport is the httpOnly
   * cookie set by register/login. Accepting it in a request body would
   * reopen exacly the XSS exposure the cookie was chosen to close.
   */
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException("No refresh token cookie present");
    }
    AuthService.AuthResult result = authService.refresh(refreshToken);
    return withRefreshCookie(HttpStatus.OK, result);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
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
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAllDevices(@AuthenticationPrincipal CustomUserDetails principal) {
    authService.logoutAllDevices(principal.getId());
    ResponseCookie expired = cookieFactory.clear();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
  }

  private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.AuthResult result) {
    ResponseCookie cookie = cookieFactory.build(result.rawRefreshToken());
    return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
  }

}
