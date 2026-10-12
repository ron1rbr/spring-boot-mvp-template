package com.example.template.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Called before issuing a new token, so a user can never have more
   * than one outstanding, usable reset link at a time; requesting
   * reset twice silently retires the first link instead of leaving two
   * valid links alive simultaneously.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
        UPDATE PasswordResetToken t
        SET t.invalidatedAt = :now
        WHERE t.user.id = :userId AND t.invalidatedAt IS NULL
      """)
  void invalidateAllActiveTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
