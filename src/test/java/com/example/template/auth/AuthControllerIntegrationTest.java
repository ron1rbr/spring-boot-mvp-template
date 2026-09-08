package com.example.template.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.common.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        .andExpect(jsonPath("$.tokanType").value("Bearer"));
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

}
