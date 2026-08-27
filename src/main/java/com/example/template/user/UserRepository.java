package com.example.template.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.template.common.exception.EntityNotFoundException;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  /**
   * Convenience wrapper so callers don't repeat the
   * findById(...).orElseThrow(...) pattern in every service. throws the
   * project's own EntityNotFoundException (RFC 7807 aware) instead of
   * letting Optional.get() throw NoSuchElementException.
   */
  default User getByIdOrThrow(UUID id) {
    return findById(id).orElseThrow(() -> EntityNotFoundException.forId("User", id));
  }
}
