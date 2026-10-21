package com.example.template.common.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.template.auth.JwtAuthenticationFilter;
import com.example.template.common.config.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * CSRF remains disabled even though a cookie now exists in the picture.
 * This is intentional, not an oversight: the refresh cookie is
 * SameSite=Strict (see RefreshTokenCookieFactory), which means browsers
 * refuse to send it on cross-site requests at all. The same protection
 * a CSRF token provides, enforced by the browser instead of application
 * code. If a feature requirement needs SameSite=None (e.g. the frontend
 * lives on a different top-level domain than the API), CSRF protection
 * must be revisited at that point.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final CorsProperties corsProperties;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/password-reset/**")
                .permitAll()
                // Docs endpoints are public here, but only EXIST when springdoc is
                // enabled (see application-prod.yml). Disabling it removes the
                // endpoints; this matcher never makes a disabled UI reachable.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Health is the only public actuator surface: platforms probe it
                // without credentials. Order matters: this must precede the
                // /actuator/** rule below.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Everything else under /actuator requires ADMIN. Not merely
                // "authenticated": registration is open, so any visitor can be.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint)
            .accessDeniedHandler(restAccessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * /api/auth/logout-all is deliberately NOT in permitAll above. It requires
   * a valid access token, same as any other protected endpoint (see
   * AuthController's Javadoc on logoutAllDevices for why).
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true); // required for the browser to send/receive the refresh cookie

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

}
