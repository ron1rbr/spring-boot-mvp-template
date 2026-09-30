package com.example.template.auth;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.template.auth.dto.AuthResponse;
import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.auth.exception.InvalidCredentialsException;
import com.example.template.common.exception.ConflictException;
import com.example.template.user.User;
import com.example.template.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final AuthenticationManager authenticationManager;

  public AuthResult register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ConflictException("Email already registered: " + request.email());
    }

    User user = User.register(request.email(), passwordEncoder.encode(request.password()), request.fullName());
    userRepository.save(user);

    return buildAuthResult(user);
  }

  public AuthResult login(LoginRequest request) {
    try {
      authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    } catch (BadCredentialsException ex) {
      throw new InvalidCredentialsException("Invalid email or password");
    }

    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

    return buildAuthResult(user);
  }

  /**
   * Presented raw refresh token in, rotated raw refresh token + new
   * access token out. AuthController is responsible for reading the
   * incoming cookie and writing the outgoing one. This method only
   * knows about tokens, never about HTTP/cookies.
   */
  public AuthResult refresh(String presentedRawRefreshToken) {
    RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(presentedRawRefreshToken);
    String accessToken = jwtService.generateAccessToken(rotation.user());
    return new AuthResult(new AuthResponse(accessToken, "Bearer", jwtService.getAccessTokenExpirationSeconds()),
        rotation.rawToken());
  }

  /**
   * Logs out the current device only: revokes just the refresh token
   * this browser/client is holding. If no refresh cookie is present,
   * this is a no-op on the token side, there's nothing to revoke, but
   * the caller (AuthController) still clears the cookie either way.
   */
  public void logoutCurrentDevice(String refreshToken) {
    if (refreshToken != null) {
      refreshTokenService.revokeSingle(refreshToken);
    }
  }

  public void logoutAllDevices(UUID userId) {
    refreshTokenService.revokeAllForUser(userId);
  }

  private AuthResult buildAuthResult(User user) {
    String accessToken = jwtService.generateAccessToken(user);
    String rawRefreshToken = refreshTokenService.issue(user);
    AuthResponse response = new AuthResponse(accessToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    return new AuthResult(response, rawRefreshToken);
  }

  /**
   * Pairs the body the client sees (AuthResponse) with the raw refresh
   * token the controller needs to put in a cookie, kept out of
   * AuthResponse itself so the refresh token can never accidentally be
   * serialized into a JSON response body.
   */
  public record AuthResult(AuthResponse response, String rawRefreshToken) {
  }

}
