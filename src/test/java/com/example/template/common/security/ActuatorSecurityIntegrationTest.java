package com.example.template.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.template.auth.dto.RegisterRequest;
import com.example.template.common.AbstractIntegrationTest;
import com.example.template.user.User;
import com.example.template.user.UserRepository;
import com.example.template.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Test
  void healthIsPublicAndHidesComponentDetails() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void livenessAndReadinessProbesArePublic() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void otherActuatorEndpointsRejectAnonymousCallers() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  void regularUsersCannotReachActuator() throws Exception {
    String token = registerAndGetAccessToken("regular@example.com");

    mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReachesExposedEndpointsButNotUnexposedOnes() throws Exception {
    String token = registerAndGetAccessToken("admin@example.com");
    promoteToAdmin("admin@example.com");

    mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());

    // Not exposed at all: even an admin get 404, not the endpoint.
    mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  private String registerAndGetAccessToken(String email) throws Exception {
    RegisterRequest request = new RegisterRequest(email, "supersecret123", "Test User");

    String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse()
        .getContentAsString();

    return JsonPath.read(body, "$.accessToken");
  }

  /**
   * The token issued at registration carries role=USER, but that doesn't
   * matter: JwtAuthenticationFilter reloads the user (and authorities)
   * from the database on every request, so the promotion applies at once.
   */
  private void promoteToAdmin(String email) {
    User user = userRepository.findByEmail(email).orElseThrow();
    user.setRole(UserRole.ADMIN);
    userRepository.save(user);
  }

}
