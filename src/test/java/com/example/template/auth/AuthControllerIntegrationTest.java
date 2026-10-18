package com.example.template.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.common.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void registerReturnsAccessToken() throws Exception {
    RegisterRequest request = new RegisterRequest("alice@example.com", "supersecret123", "Alice");

    mockMvc
        .perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void registeringSameEmailTwiceReturns409() throws Exception {
    RegisterRequest request = new RegisterRequest("bob@example.com", "supersecret123", "Bob");

    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("conflict"));
  }

  @Test
  void loginWithValidCredentialsReturnsAccessToken() throws Exception {
    RegisterRequest request = new RegisterRequest("carol@example.com", "supersecret123", "Carol");
    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)));

    LoginRequest login = new LoginRequest("carol@example.com", "supersecret123");

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(login)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  void loginWithWrongPasswordReturns401WithGenericMessage() throws Exception {
    RegisterRequest request = new RegisterRequest("dave@example.com", "supersecret123", "Dave");
    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)));

    LoginRequest login = new LoginRequest("dave@example.com", "wrong-password");

    mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(login)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("invalid-credentials"));
  }

  @Test
  void registerSetsHttpOnlyRefreshTokenCookie() throws Exception {
    RegisterRequest request = new RegisterRequest("erin@example.com", "supersecret123", "Erin");

    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(cookie().exists("refresh_token"))
        .andExpect(cookie().httpOnly("refresh_token", true));
  }

  @Test
  void refreshWithValidCookieRotatesTokenAndReturnsNewAccessToken() throws Exception {
    RegisterRequest register = new RegisterRequest("frank@example.com", "supersecret123", "Frank");

    var registerResult = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
        .andReturn();

    jakarta.servlet.http.Cookie refreshCookie = registerResult.getResponse().getCookie("refresh_token");

    mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(cookie().exists("refresh_token"));
  }

  @Test
  void refreshWithoutCookieReturns401() throws Exception {
    mockMvc.perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("invalid-refresh-token"));
  }

  @Test
  void reusingRefreshTokenAfterRotationReturns401() throws Exception {
    RegisterRequest register = new RegisterRequest("grace@example.com", "supersecret123", "Grace");

    var registerResult = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
        .andReturn();

    jakarta.servlet.http.Cookie originalCookie = registerResult.getResponse().getCookie("refresh_token");

    // First refresh: legitimate, rotates the token.
    mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie)).andExpect(status().isOk());

    // Reusing the original (now-revoked) cookie must be rejected.
    mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("refresh-token-reuse-detected"));
  }

  @Test
  void logoutWithValidAccessTokenClearsRefreshCookie() throws Exception {
    RegisterRequest register = new RegisterRequest("henry@example.com", "supersecret123", "Henry");

    var registerResult = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
        .andReturn();

    String accessToken = com.jayway.jsonpath.JsonPath.read(registerResult.getResponse().getContentAsString(),
        "$.accessToken");

    mockMvc.perform(
        post("/api/auth/logout").header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresh_token", 0));
  }

  @Test
  void logoutRevokesOnlyCurrentDeviceToken() throws Exception {
    // registra, pega dois refresh tokens simulando dois dispositivos
    // (aqui simplificado: um único registro, um único cookie)
    RegisterRequest register = new RegisterRequest("iris@example.com", "supersecret123", "Iris");
    var result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
        .andReturn();

    jakarta.servlet.http.Cookie refreshCookie = result.getResponse().getCookie("refresh_token");

    mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie)).andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresh_token", 0));

    // o token revogado não pode mais ser usado para refresh
    mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutAllDevicesRequiresAccessToken() throws Exception {
    mockMvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
  }

}
