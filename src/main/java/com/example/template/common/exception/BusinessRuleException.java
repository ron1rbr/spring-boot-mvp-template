package com.example.template.common.exception;

import org.springframework.http.HttpStatus;


/**
 * Thrown when a request is well-formed and passes field-level Bean Validation, but violates a business rule
 * that only makes sense in context, such as "refund amount connot exceed the original charge". Maps to
 * HTTP 422 (Unprocessable Entity), distinct from the 400 that Bean Validation failures produce.
 */
public class BusinessRuleException extends DomainException {
    
    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation", message);
    }

}
