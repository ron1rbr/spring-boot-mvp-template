package com.example.template.user;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.template.common.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

public class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired
  private UserRepository userRepository;

  @Test
  void saveAndRetrieveUserByEmail() {
    User user = User.register("jane@example.com", "hashed-password", "Jane Doe");
    userRepository.save(user);

    Optional<User> found = userRepository.findByEmail("jane@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getFullName()).isEqualTo("Jane Doe");
    assertThat(found.get().getRole()).isEqualTo(UserRole.USER);
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void enforcesUniqueEmailAtDatabaseLevel() {
    userRepository.save(User.register("dup@example.com", "hash1", "First"));
    userRepository.flush();

    User duplicate = User.register("dup@example.com", "hash2", "Second");

    assertThat(
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
              userRepository.save(duplicate);
              userRepository.flush();
            }))
        .isNotNull();
  }

  @Test
  void getByIdOrThrowsEntityNotFoundExceptionForMissingId() {
    java.util.UUID randomId = java.util.UUID.randomUUID();

    org.junit.jupiter.api.Assertions.assertThrows(
        com.example.template.common.exception.EntityNotFoundException.class,
        () -> userRepository.getByIdOrThrow(randomId));
  }
}
