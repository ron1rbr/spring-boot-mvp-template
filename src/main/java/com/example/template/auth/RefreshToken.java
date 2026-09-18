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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single link in a refresh token chain. Rotation never mutates a row's
 * tokenHash, instead, using a token marks THIS row revoked and creates a
 * brand new row.
 *
 * tokenHash is a SHA-256 hex digest, never the raw token, see JwtService
 * vs this class for why the hashing strategy differs from passwords.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by_token_hash", length = 64)
  private String replacedByTokenHash;

  public static RefreshToken issue(User user, String tokenHash, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.user = user;
    token.tokenHash = tokenHash;
    token.issuedAt = Instant.now();
    token.expiresAt = expiresAt;
    return token;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }

  public void markReplacedBy(String newTokenHash) {
    this.revokedAt = Instant.now();
    this.replacedByTokenHash = newTokenHash;
  }

  /**
   * True only when this token was retired BY a rotation (has a known
   * successor), the specific signal that triggers reuse detection.
   * A token revoked by logout or admin action (revokedAt set, no
   * successor) is simply invalid, not evidence of theft.
   */
  public boolean wasRotated() {
    return replacedByTokenHash != null;
  }

}
