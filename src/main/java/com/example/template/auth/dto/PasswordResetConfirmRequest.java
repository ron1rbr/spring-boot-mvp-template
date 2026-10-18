package com.example.template.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @Schema(description = "Token from the link in the reset email") @NotBlank String token,
    @Schema(example = "a-new-strong-password", minLength = 8) @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {
}
