package com.example.template.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(example = "jane@example.com") @NotBlank @Email String email,
    @Schema(example = "a-strong-password") @NotBlank String password) {
}
