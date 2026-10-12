package com.example.template.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.auth.dto.PasswordResetConfirmRequest;
import com.example.template.auth.dto.PasswordResetRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  /**
   * Always 202 Accepted, regardless of whether the email exists. see
   * PasswordResetService.requestReset's Javadoc. Returning 404 for an
   * unknow email would defeat the entire point of that design.
   */
  @PostMapping("/request")
  public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.requestReset(request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.confirmReset(request.token(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

}
