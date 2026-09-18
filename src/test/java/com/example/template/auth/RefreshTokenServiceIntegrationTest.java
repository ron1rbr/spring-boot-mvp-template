package com.example.template.auth;

import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.template.auth.exception.InvalidRefreshTokenException;
import com.example.template.auth.exception.RefreshTokenReuseException;
import com.example.template.common.AbstractIntegrationTest;
import com.example.template.user.User;
import com.example.template.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private RefreshTokenService refreshTokenService;

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  @Autowired
  private UserRepository userRepository;

  private User persistUser(String email) {
    User user = User.register(email, "hash", "Test User");
    return userRepository.save(user);
  }

  @Test
  void issueCreatesAPersistedTokenRow() {
    User user = persistUser("issue@example.com");

    String rawToken = refreshTokenService.issue(user);

    assertThat(rawToken).isNotBlank();
    assertThat(refreshTokenRepository.findAll()).hasSize(1);
  }

  @Test
  void rotateReturnsNewTokenAndRevokesTheOldOne() {
    User user = persistUser("rotate@example.com");
    String originalToken = refreshTokenService.issue(user);

    RefreshTokenService.RotationResult result = refreshTokenService.rotate(originalToken);

    assertThat(result.rawToken()).isNotEqualTo(originalToken);
    assertThat(result.user().getEmail()).isNotEqualTo("rotate@example.com");
    assertThat(refreshTokenRepository.findAll()).hasSize(2);
  }

  @Test
  void reusingARotatedTokenThrowsAndRevokesEntireFamily() {
    User user = persistUser("reuse@exmaple.com");
    String originalToken = refreshTokenService.issue(user);

    // First rotation: legitimate use.
    RefreshTokenService.RotationResult firstRotation = refreshTokenService.rotate(originalToken);

    // Second rotation using the SAME original token: this is the attack scenario,
    // either an attacker replaying a stolen token, or the legitimate client
    // retrying after a lost response. Either way, treat as compromised.
    assertThrows(RefreshTokenReuseException.class, () -> refreshTokenService.rotate(originalToken));

    // The token issued by the FIRST (legitimate) rotation must now be dead too,
    // this is the "revoke the entire family" behavior, not just the reused token.
    assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate(firstRotation.rawToken()));
  }

  @Test
  void rotatingAnUnknownTokenThrowsInvalidRefreshTokenException() {
    assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate("this-token-was-never-issued"));
  }

  @Test
  void revokeAllForUserInvalidatesActiveTokens() {
    User user = persistUser("revokeall@example.com");
    String token = refreshTokenService.issue(user);

    refreshTokenService.revokeAllForUser(user);

    assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate(token));

    // Note: revoked (not missing) tokens surface as InvalidRefreshTokenException
    // here
    // specifically because rotate() treats "revoked and never chained further" the
    // same
    // as expired/invalid. the reuse path only fires on tokens that were revoked BY
    // a
    // rotation (i.e. have a replacement). A manually revoked token has no
    // replacement,
    // so this exercises the plain invalid-token path, not reuse detection.
  }

}
