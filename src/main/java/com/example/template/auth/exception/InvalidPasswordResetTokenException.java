package com.example.template.auth.exception;

import org.springframework.http.HttpStatus;

import com.example.template.common.exception.DomainException;

/**
 * Covers "not found", "expired", and "already invalidated" uniformly,
 * unlike refresh tokens, a password reset token has no rotation chain,
 * so there's no separate reuse-detection signal to preserve here. Any
 * problem with the token is just "this reset link no longer works".
 */
public class InvalidPasswordResetTokenException extends DomainException {

  public InvalidPasswordResetTokenException(String message) {
    super(HttpStatus.BAD_REQUEST, "invalid-password-reset-token", message);
  }

}
