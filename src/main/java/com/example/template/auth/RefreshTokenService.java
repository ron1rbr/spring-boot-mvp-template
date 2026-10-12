package com.example.template.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.template.auth.exception.InvalidRefreshTokenException;
import com.example.template.auth.exception.RefreshTokenReuseException;
import com.example.template.common.security.OpaqueTokenGenerator;
import com.example.template.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final OpaqueTokenGenerator tokenGenerator;

  @Transactional
  public String issue(User user) {
    String rawToken = tokenGenerator.generateRawToken();
    Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

    RefreshToken token = RefreshToken.issue(user, tokenGenerator.hash(rawToken), expiresAt);
    refreshTokenRepository.save(token);

    return rawToken;
  }

  /**
   * noRollbackFor is essential, not decorative: in the reuse path, this method
   * REVOKES the entire token family and THEN throws the exception. Without it,
   * the default rollback behavior for RuntimeException would undo the revocation
   * that just occurred, turning theft detection into a warning with no actual
   * effect.
   */
  @Transactional(noRollbackFor = RefreshTokenReuseException.class)
  public RotationResult rotate(String rawToken) {
    String presentedHash = tokenGenerator.hash(rawToken);

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
    String newRawToken = tokenGenerator.generateRawToken();
    String newHash = tokenGenerator.hash(newRawToken);
    Instant newExpiresAt = Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

    existing.markReplacedBy(newHash);
    refreshTokenRepository.save(existing);

    RefreshToken next = RefreshToken.issue(user, newHash, newExpiresAt);
    refreshTokenRepository.save(next);

    return new RotationResult(newRawToken, user);
  }

  @Transactional
  public void revokeSingle(String rawToken) {
    refreshTokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
        .filter(token -> !token.isRevoked())
        .ifPresent(token -> {
          token.revoke();
          refreshTokenRepository.save(token);
        });
  }

  @Transactional
  public void revokeAllForUser(UUID userId) {
    refreshTokenRepository.revokeAllActiveTokensForUser(userId, Instant.now());
  }

  public record RotationResult(String rawToken, User user) {
  }

}
