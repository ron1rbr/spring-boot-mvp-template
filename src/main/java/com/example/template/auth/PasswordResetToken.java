package com.example.template.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.example.template.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Single-use, non-chained: unlike RefreshToken, there's no rotation
 * concept here. invalidatedAt covers two distinct events with the same
 * effect (this token can never be used again): the token was actually
 * consumed via confirmReset, OR it was superseded by a newer reset
 * request for the same user. The service layer doesn't need to tell
 * these apart; both just mean "no longer valid".
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "invalidated_at")
  private Instant invalidatedAt;

  public static PasswordResetToken issue(User user, String tokenHash, Instant expiresAt) {
    PasswordResetToken token = new PasswordResetToken();
    token.user = user;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    return token;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isInvalidated() {
    return invalidatedAt != null;
  }

  public void invalidate() {
    this.invalidatedAt = Instant.now();
  }

}
