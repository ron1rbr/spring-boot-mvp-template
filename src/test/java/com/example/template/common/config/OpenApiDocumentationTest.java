package com.example.template.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.template.common.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class OpenApiDocumentationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void apiDocsArePublicAndDescribeAuthEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/password-reset/request']").exists());
  }

  @Test
  void declaresBearerSchemeAndRequiresItGlobally() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.security[0].bearerAuth").exists());
  }

  @Test
  void publicEndpointsOptOutOfTheGlobalSecurityRequirement() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
  }

}
