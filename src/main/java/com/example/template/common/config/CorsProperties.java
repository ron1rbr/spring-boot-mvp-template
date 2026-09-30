package com.example.template.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * The frontend origin(s) allowed to make credentialed requests (i.e. send
 * and receive the httpOnly refresh cookie). Deliberately NOT a wildcard
 * ("*") anywhere in this config. CORS with credentials=true and a
 * wildcard origin is rejected by browsers anyway, and would be a security
 * hole if it weren't.
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
@Validated
@Getter
@Setter
public class CorsProperties {

  @NotEmpty
  private List<String> allowedOrigins;

}
