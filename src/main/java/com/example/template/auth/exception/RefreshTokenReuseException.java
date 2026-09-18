package com.example.template.auth.exception;

import org.springframework.http.HttpStatus;

import com.example.template.common.exception.DomainException;

/**
 * Thrown when an already-rotated (revoked) refresh token is presented
 * again. The strongest signal this template has that a token was stolen
 * and used concurrently with the legitimate owner. By the time this
 * exception is thrown, RefreshTokenService has already revoked the
 * user's entire token family; this exception just communicates the
 * 401 back to the caller, who must log in again.
 */
public class RefreshTokenReuseException extends DomainException {

  public RefreshTokenReuseException(String message) {
    super(HttpStatus.UNAUTHORIZED, "refresh-token-reuse-detected", message);
  }

}
