package com.example.template.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Declares the API's identity and its single security scheme (Bearer JWT).
 *
 * The security requirement is applied GLOBALLY, mirroring SecurityConfig's
 * anyRequest().authenticated(): every operation is documented as protected
 * unless it explicitly opts out with @SecurityRequirements (empty). A new
 * endpoint that forgets to think about auth is documented as protected,
 * which is also how it behaves at runtime.
 */
@Configuration
public class OpenApiConfig {

  public static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI openAPI(@Value("${spring.application.name}") String applicationName) {
    return new OpenAPI()
        .info(new Info().title(applicationName + " API").version("v1").description("""
            Authentication uses a short-lived access token (Bearer JWT, sent in the \
            Authorization header) plus a refresh token that lives only in an httpOnly \
            cookie and is never exposed in any response body.
            """))
        .components(new Components().addSecuritySchemes(BEARER_SCHEME,
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }

}
