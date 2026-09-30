package com.example.template.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Single place that decides how the refresh token cookie is shaped.
 * Anyone changing cookie attributes (path, SameSite policy, secure flag)
 * changes it here once. No endpoint builds a cookie by hand.
 *
 * Uses ResponseCookie (not the raw servlet Cookie API) because it's the
 * only way to set SameSite from Java. The servlet Cookie class has no
 * such method.
 */
@Component
public class RefreshTokenCookieFactory {

  public static final String COOKIE_NAME = "refresh_token";
  private static final String COOKIE_PATH = "/api/auth";

  private final JwtProperties jwtProperties;

  public RefreshTokenCookieFactory(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  /**
   * Path is scoped to /api/auth, not "/" because the cookie has no reason to
   * be sent on every request to the API, only the handful of auth
   * endpoints that actually read it. Narrower scope, smaller attack
   * surface if a future endpoint under a different path were ever
   * vulnerable to something that leaks request headers/cookies.
   */
  public ResponseCookie build(String rawToken) {
    return ResponseCookie.from(COOKIE_NAME, rawToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(java.time.Duration.ofDays(jwtProperties.getRefreshTokenExpirationDays()))
        .build();
  }

  /**
   * An expired, empty cookie, the standard way to tell a browser to
   * delete a cookie is to resend it with the same name/path and a
   * maxAge of zero.
   */
  public ResponseCookie clear() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(0)
        .build();
  }

}
