package com.example.template.auth;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.auth.dto.PasswordResetConfirmRequest;
import com.example.template.auth.dto.PasswordResetRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
@SecurityRequirements // every endpoint here is public: the caller has no session by definition
@Tag(name = "Password reset", description = "Forgot-password flow using single-use emailed tokens")
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  /**
   * Always 202 Accepted, regardless of whether the email exists. see
   * PasswordResetService.requestReset's Javadoc. Returning 404 for an
   * unknow email would defeat the entire point of that design.
   */
  @Operation(summary = "Request a password reset email", description = "Always answers 202, whether or not the email belongs to an account, so callers cannot discover which emails are registered.")
  @ApiResponse(responseCode = "202", description = "Request accepted")
  @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/request")
  public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.requestReset(request.email());
    return ResponseEntity.accepted().build();
  }

  @Operation(summary = "Set a new password using a reset token", description = "On success every active session of the account is revoked.")
  @ApiResponse(responseCode = "204", description = "Password changed")
  @ApiResponse(responseCode = "400", description = "Validation failed, or token invalid/expired/already used", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.confirmReset(request.token(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

}
