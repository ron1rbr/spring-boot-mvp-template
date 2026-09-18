package com.example.template.auth.exception;

import org.springframework.http.HttpStatus;

import com.example.template.common.exception.DomainException;

/**
 * Thrown for a refresh token that's simply unusable: not found, expired,
 * or malformed. Distinct from RefreshTokenReuseException, which signals
 * something more serious than an expired session.
 */
public class InvalidRefreshTokenException extends DomainException {

  public InvalidRefreshTokenException(String message) {
    super(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", message);
  }

}
