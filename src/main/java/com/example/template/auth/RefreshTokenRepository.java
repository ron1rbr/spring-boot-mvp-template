package com.example.template.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Bulk-revokes every still-active token for a user in a single UPDATE,
   * rather than loading each row into memory to call revoke() + save().
   * Used when reuse is detected: the entire family must die at once, and
   * this must be fast and atomic since it's the incident-response path.
   *
   * flushAutomatically: flushes pending changes from the persistence context
   * before the bulk UPDATE, so the query can see the latest state.
   * clearAutomatically: a bulk UPDATE bypasses Hibernate, so already-loaded
   * entities would have an outdated revokedAt value in memory.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
        UPDATE RefreshToken t
        SET t.revokedAt = :now
        WHERE t.user.id = :userId AND t.revokedAt IS NULL
      """)
  void revokeAllActiveTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
