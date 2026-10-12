package com.example.template.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.password-reset")
@Validated
@Getter
@Setter
public class PasswordResetProperties {

  @Positive
  private long expirationMinutes;

  /**
   * Base URL of the FRONTEND page that reads ?token=... and calls
   * /api/auth/password-reset/confirm. Never a backend URL. The API
   * itself has no reset-password page to render.
   */
  @NotBlank
  private String resetUrlBase;

}
