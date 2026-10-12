package com.example.template.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.template.auth.exception.InvalidPasswordResetTokenException;
import com.example.template.common.email.EmailSender;
import com.example.template.common.security.OpaqueTokenGenerator;
import com.example.template.user.User;
import com.example.template.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private final PasswordResetTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;
  private final EmailSender emailSender;
  private final OpaqueTokenGenerator tokenGenerator;
  private final PasswordResetProperties properties;

  /**
   * Always completes silently, whether or not the email belongs to a
   * real account; this is the same enumeration-prevention principle
   * established for login in Commit 5 (InvalidCredentialsException's
   * unified message). The controller returns 202 Accepted either way;
   * the caller can never distinguish "email sent" from "no such user".
   */
  @Transactional
  public void requestReset(String email) {
    userRepository.findByEmail(email).ifPresent(user -> {
      tokenRepository.invalidateAllActiveTokensForUser(user.getId(), Instant.now());

      String rawToken = tokenGenerator.generateRawToken();
      Instant expiresAt = Instant.now().plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
      tokenRepository.save(PasswordResetToken.issue(user, tokenGenerator.hash(rawToken), expiresAt));

      String resetLink = properties.getResetUrlBase() + "?token=" + rawToken;
      emailSender.send(
          user.getEmail(),
          "Reset your password",
          "We received a request to reset your password. Use the link below " + "(expires in "
              + properties.getExpirationMinutes() + " minutes):\n\n" + resetLink
              + "\n\nIf you didn't request this, you can safely ignore this email.");
    });
  }

  /**
   * On success, revokes every refresh token the user has. A password
   * change is exacly the kind of event that should force re-login on
   * every device, whether the change was the owner's own precaution or
   * evidence their account was compromised.
   */
  @Transactional
  public void confirmReset(String rawToken, String newPassword) {
    PasswordResetToken token = tokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
        .orElseThrow(() -> new InvalidPasswordResetTokenException("This reset link is invalid"));

    if (token.isInvalidated() || token.isExpired()) {
      throw new InvalidPasswordResetTokenException("This reset link has expired or was already used");
    }

    User user = token.getUser();
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    token.invalidate();
    tokenRepository.save(token);

    refreshTokenService.revokeAllForUser(user.getId());
  }

}
