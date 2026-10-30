package com.example.template.common.ratelimit;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs inside the Spring Security chain, before the JWT filter (see
 * SecurityConfig): throttling comes first, so a flood of requests never
 * costs a token parse or a database lookup.
 *
 * The client key is request.getRemoteAddr(). Behind a proxy that value is
 * the real client IP only because server.forward-headers-strategy=native
 * lets Tomcat resolve X-Forwarded-For from trusted proxies; this class
 * deliberately never reads that header itself, since a header read here
 * would be forgeable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitService rateLimitService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    // Decoded and normalized exacly the way Spring MVC will see the path.
    // The raw request URI is NOT safe to match: "/api/auth/%6cogin" reaches
    // the login controller but would never match a "/api/auth/login" rule.
    String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    String clientKey = request.getRemoteAddr();

    RateLimitService.Decision decision = rateLimitService.check(path, clientKey);
    if (decision.allowed()) {
      filterChain.doFilter(request, response);
      return;
    }

    log.debug("Rate limit exceeded: client={}, path={}", clientKey, path);
    reject(response, decision.retryAfterSeconds());
  }

  private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
        "Too many requests. Try again in " + retryAfterSeconds + "seconds.");
    problemDetail.setProperty("errorCode", "rate-limit-exceeded");
    problemDetail.setProperty("timestamp", Instant.now());

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), problemDetail);
  }

}
