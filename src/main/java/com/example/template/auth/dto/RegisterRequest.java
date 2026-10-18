package com.example.template.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Schema(example = "jane@example.com") @NotBlank @Email String email,
    @Schema(example = "a-strong-password", minLength = 8) @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
    @Schema(example = "Jane Doe") @NotBlank String fullName) {
}
