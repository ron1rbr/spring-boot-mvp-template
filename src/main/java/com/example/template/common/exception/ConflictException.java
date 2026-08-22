package com.example.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with the current state of a resource. Example include registering an
 * email that's already taken or reusing a single-use token. Maps to HTTP 409.
 */
public class ConflictException extends DomainException {
    
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }

}
