package com.example.template.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsEntityNotFoundExceptionTo404WithProblemDetail() {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/users/42"));

    ResponseEntity<Object> response = handler.handleDomainException(
        EntityNotFoundException.forId("User", 42), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo("User not found with id: 42");
    assertThat(body.getProperties()).containsEntry("errorCode", "entity-not-found");
  }

  @Test
  void mapsConflictExceptionTo409() {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/users"));

    ResponseEntity<Object> response = handler.handleDomainException(
        new ConflictException("Email already registered"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void mapsBusinessExceptionTo422() {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/orders/1/refund"));

    ResponseEntity<Object> response = handler.handleDomainException(
        new BusinessRuleException("Refund exceeds original charge"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

}
