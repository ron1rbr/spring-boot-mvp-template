package com.example.template.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds) {
}
