package com.example.template.common.ratelimit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Binds app.rate-limit.*. Each rule is a named group of paths sharing one
 * budget per client: capacity tokens, refilled gradually over the window.
 * The application refuses to start with a malformed rule.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
@Getter
@Setter
public class RateLimitProperties {

  private boolean enabled = true;

  /**
   * Upper bound of distinct clients remembered PER RULE, which caps memory
   * use no matter how many IPs hit the API.
   */
  @Positive
  private int maxTrackedClients = 10_000;

  @Valid
  private Map<String, Rule> rules = new LinkedHashMap<>();

  @Getter
  @Setter
  public static class Rule {

    /** Ant-style patterns, matched against the path inside the application. */
    @NotEmpty
    private List<String> paths;

    @Positive
    private long capacity;

    @NotNull
    private Duration window;

  }

}
