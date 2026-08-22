package com.example.template.common.exception;

import org.springframework.http.HttpStatus;

/**
* Base type for every exception that represents a business/domain rule
* violation, as opposed to an unexpected technical failure.
*
* Every subclass declare its own HTTP status and a stable, machine-readable
* error code (used as the "type" suffix in the ProblemDetail response, e.g.
* "entity-not-found"). New features should extend this class (or one of its
* existing subclasses) instead of throwing raw RuntimeException, so that
* GlobalExceptionHandler can map it correctly without special-casing.
*/
public abstract class DomainException extends RuntimeException {
    
    private final HttpStatus status;
    private final String errorCode;

    protected DomainException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
