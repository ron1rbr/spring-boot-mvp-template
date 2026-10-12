package com.example.template.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.template.auth.exception.InvalidPasswordResetTokenException;
import com.example.template.auth.exception.InvalidRefreshTokenException;
import com.example.template.common.AbstractIntegrationTest;
import com.example.template.common.email.EmailSender;
import com.example.template.user.User;
import com.example.template.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

class PasswordResetServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PasswordResetService passwordResetService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RefreshTokenService refreshTokenService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockBean
  private EmailSender emailSender;

  @BeforeEach
  void resetMock() {
    // Each test gets a clean interaction history; the Spring context
    // (and this mock bean) is cached and reused across test methods.
    reset(emailSender);
  }

  private User persistUser(String email) {
    User user = User.register(email, passwordEncoder.encode("originalPassword123"), "Test User");
    return userRepository.save(user);
  }

  private String captureResetToken(String email) {
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(email), anyString(), bodyCaptor.capture());
    String body = bodyCaptor.getValue();
    return body.substring(body.indexOf("token=") + "token=".length()).split("\\s")[0];
  }

  @Test
  void requestResetSendsEmailContainingToken() {
    User user = persistUser("resetme@example.com");

    passwordResetService.requestReset(user.getEmail());

    String token = captureResetToken(user.getEmail());
    assertThat(token).isNotBlank();
  }

  @Test
  void requestResetForUnknownEmailSendsNoEmail() {
    // No exception either; external behavior is identical to a known
    // email, which is the entire point of the enumeration protection.
    passwordResetService.requestReset("nobody@example.com");

    verifyNoInteractions(emailSender);
  }

  @Test
  void confirmResetChangesPasswordAndRevokesAllRefreshTokens() {
    User user = persistUser("confirmflow@example.com");
    String activeRefreshToken = refreshTokenService.issue(user);

    passwordResetService.requestReset(user.getEmail());
    String resetToken = captureResetToken(user.getEmail());

    passwordResetService.confirmReset(resetToken, "brandNewPassword123");

    User updated = userRepository.getByIdOrThrow(user.getId());
    assertThat(passwordEncoder.matches("brandNewPassword123", updated.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("originalPassword123", updated.getPasswordHash())).isFalse();

    // The session that existed BEFORE the reset must now be dead.
    assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate(activeRefreshToken));
  }

  @Test
  void reusingAConsumedResetTokenFails() {
    User user = persistUser("reuseresettoken@example");
    passwordResetService.requestReset(user.getEmail());
    String token = captureResetToken(user.getEmail());

    passwordResetService.confirmReset(token, "firstNewPassword123");

    assertThrows(InvalidPasswordResetTokenException.class,
        () -> passwordResetService.confirmReset(token, "secondNewPassword123"));
  }

  @Test
  void confirmingWithUnknownTokenThrows() {
    assertThrows(InvalidPasswordResetTokenException.class,
        () -> passwordResetService.confirmReset("never-issued-token", "somePassword123"));
  }

  @Test
  void requestingResetAgainInvalidatesThePreviousToken() {
    User user = persistUser("tokenchurn@example.com");

    passwordResetService.requestReset(user.getEmail());
    String firstToken = captureResetToken(user.getEmail());

    reset(emailSender); // isolate the capture for the second request
    passwordResetService.requestReset(user.getEmail());
    String secondToken = captureResetToken(user.getEmail());

    assertThrows(InvalidPasswordResetTokenException.class,
        () -> passwordResetService.confirmReset(firstToken, "irrelevantPassword123"));

    passwordResetService.confirmReset(secondToken, "finalPassword123");

    User updated = userRepository.getByIdOrThrow(user.getId());
    assertThat(passwordEncoder.matches("finalPassword123", updated.getPasswordHash())).isTrue();
  }

}
