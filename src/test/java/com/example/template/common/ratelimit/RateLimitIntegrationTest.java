package com.example.template.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.common.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rate limiting is switched off in the test profile; this class turns it
 * back on with small capacities. Counters lives in memory for the lifetime of
 * this class's application context, so every test uses its own client
 * addresses instead of resetting state.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.rate-limit.enabled=true",
    "app.rate-limit.rules.login.capacity=3",
    "app.rate-limit.rules.register.capacity=2"
})
class RateLimitIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void loginIsLimitedPerClientAddress() throws Exception {
    for (int i = 0; i < 3; i++) {
      attemptLogin("10.1.0.1").andExpect(status().isUnauthorized());
    }

    attemptLogin("10.1.0.1").andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.errorCode").value("rate-limit-exceeded"));
  }

  @Test
  void anotherClientIsNotAffected() throws Exception {
    for (int i = 0; i < 3; i++) {
      attemptLogin("10.2.0.1");
    }

    attemptLogin("10.2.0.1").andExpect(status().isTooManyRequests());

    attemptLogin("10.2.0.2").andExpect(status().isUnauthorized());
  }

  @Test
  void groupsHaveIndependentBudgets() throws Exception {
    for (int i = 0; i < 3; i++) {
      attemptLogin("10.3.0.1");
    }

    attemptLogin("10.3.0.1").andExpect(status().isTooManyRequests());

    register("independent-budget@example.com", "10.3.0.1").andExpect(status().isCreated());
  }

  @Test
  void registerIsLimitedToItsOwnCapacity() throws Exception {
    register("limit-one@example.com", "10.4.0.1").andExpect(status().isCreated());
    register("limit-two@example.com", "10.4.0.1").andExpect(status().isCreated());

    register("limit-three@example.com", "10.4.0.1").andExpect(status().isTooManyRequests());
  }

  @Test
  void pathsWithoutARuleAreNotLimited() throws Exception {
    for (int i = 0; i < 10; i++) {
      mockMvc.perform(get("/actuator/health").with(fromAddress("10.5.0.1"))).andExpect(status().isOk());
    }
  }

  private ResultActions attemptLogin(String address) throws Exception {
    LoginRequest body = new LoginRequest("nobody@example.com", "wrong-password");
    return mockMvc.perform(post("/api/auth/login").with(fromAddress(address)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  private ResultActions register(String email, String address) throws Exception {
    RegisterRequest body = new RegisterRequest(email, "supersecret123", "Rate Limit Test");
    return mockMvc.perform(post("/api/auth/register").with(fromAddress(address)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  private static RequestPostProcessor fromAddress(String address) {
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }

}
