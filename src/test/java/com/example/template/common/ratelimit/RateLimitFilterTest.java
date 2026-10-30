package com.example.template.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

  private RateLimitFilter filterWithLoginCapacity(long capacity) {
    RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
    rule.setPaths(List.of("/api/auth/login"));
    rule.setCapacity(capacity);
    rule.setWindow(Duration.ofMinutes(1));

    RateLimitProperties properties = new RateLimitProperties();
    properties.setRules(Map.of("login", rule));

    return new RateLimitFilter(new RateLimitService(properties),
        new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  private MockHttpServletRequest post(String uri, String clientIp) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRemoteAddr(clientIp);
    return request;
  }

  private MockFilterChain run(RateLimitFilter filter, MockHttpServletRequest request,
      MockHttpServletResponse response) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return chain;
  }

  @Test
  void rejectsWithRetryAfterOnceCapacityIsExhausted() throws Exception {
    RateLimitFilter filter = filterWithLoginCapacity(2);

    for (int i = 0; i < 2; i++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = run(filter, post("/api/auth/login", "203.0.113.1"), response);
      assertThat(chain.getRequest()).as("request %d should pass", i + 1).isNotNull();
    }

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    MockFilterChain chain = run(filter, post("/api/auth/login", "203.0.113.1"), blocked);

    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isPositive();
    assertThat(chain.getRequest()).as("the controller must not be reached").isNull();
  }

  @Test
  void percentEncodedPathSharesTheSameBucket() throws Exception {
    RateLimitFilter filter = filterWithLoginCapacity(1);

    MockFilterChain first = run(filter, post("/api/auth/login", "203.0.113.2"), new MockHttpServletResponse());
    assertThat(first.getRequest()).isNotNull();

    // Same endpoint as far as Spring MVC is concerned ("%6c" is "l").
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    MockFilterChain second = run(filter, post("/api/auth/%6cogin", "203.0.113.2"), blocked);

    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(second.getRequest()).isNull();
  }

  @Test
  void differentClientsHaveSeparateBuckets() throws Exception {
    RateLimitFilter filter = filterWithLoginCapacity(1);

    assertThat(run(filter, post("/api/auth/login", "203.0.113.3"), new MockHttpServletResponse()).getRequest())
        .isNotNull();
    assertThat(run(filter, post("/api/auth/login", "203.0.113.4"), new MockHttpServletResponse()).getRequest())
        .isNotNull();

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    run(filter, post("/api/auth/login", "203.0.113.3"), blocked);
    assertThat(blocked.getStatus()).isEqualTo(429);
  }

  @Test
  void pathsWithoutARuleAreNeverLimited() throws Exception {
    RateLimitFilter filter = filterWithLoginCapacity(1);

    for (int i = 0; i < 5; i++) {
      MockFilterChain chain = run(filter, post("/api/something-else", "203.0.113.5"), new MockHttpServletResponse());
      assertThat(chain.getRequest()).isNotNull();
    }
  }

}
