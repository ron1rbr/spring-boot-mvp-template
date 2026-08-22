package com.example.template.common.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single point of translation from exceptions to HTTP responses, using
 * RFC 7807 (application/problem+json) as the wire format.
 * 
 * Extends ResponseEntityExceptionHandler to override Spring's own
 * MethodArgumentNotValidException handling (triggered by @Valid on
 * request DTOs) rather than reimplementing it. Spring already builds
 * a ProblemDetail for it, we just enrich it with field-level detail.
 * 
 * Every DomainException subclass is handled generically via its own
 * status/errorCode. new domain exceptions never require touching this
 * class, witch is the entire point of the hierarchy in DomainException.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Object> handleDomainException(DomainException ex, WebRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    enrich(problemDetail, ex.getErrorCode(), request);
    return ResponseEntity.status(ex.getStatus()).body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    // Catch-all safety net: never leak stack traces or internal messages
    // to the client for anything we didn't explicitly anticipate.
    logger.error("Unhandled exception", ex);
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred");
    enrich(problemDetail, "internal-error", request);
    return ResponseEntity.internalServerError().body(problemDetail);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }

    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "One or more fields are invalid");
    enrich(problemDetail, "validation-failed", request);
    problemDetail.setProperty("errors", fieldErrors);

    return ResponseEntity.badRequest().body(problemDetail);
  }

  private void enrich(ProblemDetail problemDetail, String errorCode, WebRequest request) {
    problemDetail.setType(java.net.URI.create("https://api.example.com.errors/" + errorCode));
    problemDetail.setProperty("errorCode", errorCode);
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("path", request.getDescription(false).replace("uri=", ""));
  }
}
