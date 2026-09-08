package com.example.template.auth.exception;

import org.springframework.http.HttpStatus;

import com.example.template.common.exception.DomainException;

/**
 * Thrown for any login failure, wrong password AND unknown email map to
 * this same exception with the same generic message. Never reveal which
 * of the two failed; that distinction is exacly what lets a attacker
 * enumerate registered emails.
 */
public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException(String message) {
    super(HttpStatus.UNAUTHORIZED, "invalid-credentials", message);
  }

}
