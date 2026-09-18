package com.example.template.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.template.auth.exception.InvalidRefreshTokenException;
import com.example.template.auth.exception.RefreshTokenReuseException;
import com.example.template.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTE_LENGTH = 32; // 256 bits of entropy

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Issues a brand new token chain for a user, called on login/register,
   * never as part of rotation (rotate() creates its own follow-up token).
   */
  public String issue(User user) {
    String rawToken = generateRawToken();
    Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(),
        java.time.temporal.ChronoUnit.DAYS);

    RefreshToken token = RefreshToken.issue(user, hash(rawToken), expiresAt);
    refreshTokenRepository.save(token);

    return rawToken;
  }

  /**
   * Validates a presented refresh token and, if valid, atomically retires
   * it and issues its replacement. Returns the new raw token plus the
   * associated user, so the caller can also mint a fresh access token.
   *
   * Reuse detection: if the token is found but was already rotated
   * once before, presenting it again can only happen if it leaked and
   * is being used by two parties at once. The entire family is revoked
   * immediately; the legitimate owner will simply be asked to log in
   * again, which is an acceptable cost for shutting down a compromised
   * session immediately.
   */
  public RotationResult rotate(String rawToken) {
    String presentedHash = hash(rawToken);

    RefreshToken existing = refreshTokenRepository.findByTokenHash(presentedHash)
        .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));

    if (existing.wasRotated()) {
      log.warn("Refresh token reuse detected for user {}. Revoking entire token family.", existing.getUser().getId());
      refreshTokenRepository.revokeAllActiveTokensForUser(existing.getUser().getId(), Instant.now());
      throw new RefreshTokenReuseException(
          "This refresh token was already used. All sessions for this account have been revoked.");
    }

    if (existing.isRevoked() || existing.isExpired()) {
      throw new InvalidRefreshTokenException("Refresh token is no longer valid");
    }

    User user = existing.getUser();
    String newRawToken = generateRawToken();
    String newHash = hash(newRawToken);
    Instant newExpiresAt = Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(),
        java.time.temporal.ChronoUnit.DAYS);

    existing.markReplacedBy(newHash);
    refreshTokenRepository.save(existing);

    RefreshToken next = RefreshToken.issue(user, newHash, newExpiresAt);
    refreshTokenRepository.save(next);

    return new RotationResult(newRawToken, user);
  }

  /**
   * Revokes every active token for a user, used for an explicit
   * "log out everywhere" action (wired up when that endpoint exists).
   */
  public void revokeAllForUser(User user) {
    refreshTokenRepository.revokeAllActiveTokensForUser(user.getId(), Instant.now());
  }

  private String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM, this branch is unreachable.
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }

  public record RotationResult(String rawToken, User user) {
  }

}
