package com.example.template.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
    @Schema(description = "Short-lived JWT; send it as 'Authorization: Bearer <token>'") String accessToken,
    @Schema(example = "Bearer") String tokenType,
    @Schema(description = "Access token lifetime in seconds", example = "900") long expiresInSeconds) {
}
