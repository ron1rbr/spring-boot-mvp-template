package com.example.template.auth;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.user.User;

class JwtServiceTest {

  private JwtProperties propertiesWithExpiration(long minutes) {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("unit-test-secret-key-not-for-real-use-000000");
    properties.setAccessTokenExpirationMinutes(minutes);
    return properties;
  }

  private User buildUser() {
    User user = User.register("jane@example.com", "hash", "Jane Doe");
    // id is normally assigned by Hibernate on persist; set via reflection-free
    // path isn't available here, so this test only exercises fields JwtService
    // actually reads (email/role). id being null is fine for token generation.
    return user;
  }

  @Test
  void generateAndParsesTokenRoundTrip() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(15));
    User user = buildUser();

    String token = jwtService.generateAccessToken(user);

    assertThat(jwtService.extractEmail(token)).isEqualTo("jane@example.com");
  }

  @Test
  void isTokenValidReturnsTrueForMatchingUser() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(15));
    User user = buildUser();
    String token = jwtService.generateAccessToken(user);

    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("jane@example.com");

    assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
  }

  @Test
  void isTokenValidReturnsFalseForDifferentUser() {
    JwtService jwtService = new JwtService(propertiesWithExpiration(15));
    String token = jwtService.generateAccessToken(buildUser());

    UserDetails otherUser = mock(UserDetails.class);
    when(otherUser.getUsername()).thenReturn("someone-else@example.com");

    assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
  }

  @Test
  void isTokenValidReturnsFalseForExpiredToken() {
    // Negative expiration forces the "expiration" claim into the past.
    JwtService jwtService = new JwtService(propertiesWithExpiration(-1));
    String token = jwtService.generateAccessToken(buildUser());

    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("jane@example.com");

    assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
  }

}
