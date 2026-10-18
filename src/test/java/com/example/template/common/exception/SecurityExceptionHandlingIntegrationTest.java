package com.example.template.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.auth.dto.RegisterRequest;
import com.example.template.common.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The template has no @PreAuthorize endpoint yet, so this test brings its
 * own probe controller. It is registered explicitly via @Import, which keeps
 * it out of every other test's application context.
 */
@AutoConfigureMockMvc
@Import(SecurityExceptionHandlingIntegrationTest.AdminProbeController.class)
class SecurityExceptionHandlingIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  // public class + public method: method security proxies the bean with
  // CGLIB, which is unreliable for package-private members.
  @RestController
  @RequestMapping("/test-probe")
  public static class AdminProbeController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
      return "ok";
    }

  }

  @Test
  void methodSecurityDenialReturns403ProblemDetailInsteadOf500() throws Exception {
    String accessToken = registerAndGetAccessToken("probe-user@example.com");

    mockMvc.perform(get("/test-probe/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.errorCode").value("forbidden"));
  }

  private String registerAndGetAccessToken(String email) throws Exception {
    RegisterRequest request = new RegisterRequest(email, "supersecret123", "Probe User");

    String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse()
        .getContentAsString();

    return JsonPath.read(body, "$.accessToken");
  }

}
