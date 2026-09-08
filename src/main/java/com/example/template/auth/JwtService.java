package com.example.template.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.template.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Generates and validates HS256 access tokens. Deliberately does NOT
 * handle refresh tokens, those are a persisted, revocable concept
 * introduced in the next commit, not a JWT concern at all.
 */
@Service
public class JwtService {

  private final JwtProperties properties;
  private final SecretKey key;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(properties.getAccessTokenExpirationMinutes() * 60);

    return Jwts.builder()
        .subject(user.getEmail())
        .claims(Map.of("userId", user.getId().toString(), "role", user.getRole().name()))
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(key)
        .compact();
  }

  public long getAccessTokenExpirationSeconds() {
    return properties.getAccessTokenExpirationMinutes() * 60;
  }

  /**
   * Returns the subject (email) if the token is well-formed and signed
   * correctly; does NOT check expiration on its own; pair with
   * isTokenValid() before trusting the identity.
   */
  public String extractEmail(String token) {
    return parseClaims(token).getSubject();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    try {
      Claims claims = parseClaims(token);
      boolean emailMatches = claims.getSubject().equals(userDetails.getUsername());
      boolean notExpired = claims.getExpiration().after(Date.from(Instant.now()));
      return emailMatches && notExpired;
    } catch (JwtException ex) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

}
