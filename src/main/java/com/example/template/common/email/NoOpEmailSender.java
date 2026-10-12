package com.example.template.common.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Active only under the "test" profile (see AbstractIntegrationTest,
 * which activates it). Integration tests exercise the full
 * PasswordResetService flow without ever touching a real SMTP server.
 * Tests assert against what WOULD have been sent, via a mocked
 * EmailSender bean in the test itself, not this class's log line.
 */
@Component
@Profile("test")
@Slf4j
public class NoOpEmailSender implements EmailSender {

  @Override
  public void send(String to, String subject, String body) {
    log.info("Suppressed email (test profile) - to={}, subject={}", to, subject);
  }

}
