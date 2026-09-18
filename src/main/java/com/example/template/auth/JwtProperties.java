package com.example.template.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Binds the jwt.* properties (see application-{profile}.yml). Validated at
 * startup. the application refuses to boot with a secret shorter than 32
 * characteres (256 bit), which is the minimum HS256 requires to be secure.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

  @NotBlank
  @Size(min = 32, message = "jwt.secret must be at least 32 characteres (256 bit) for HS256")
  private String secret;

  @Positive
  private long accessTokenExpirationMinutes;

  @Positive
  private long refreshTokenExpirationDays;

}
