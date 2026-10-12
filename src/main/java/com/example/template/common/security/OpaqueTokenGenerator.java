package com.example.template.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates and hashes high-entropy opaque tokens, extracted from
 * RefreshTokenService once PasswordResetService needed the exact same
 * pattern. Any future feature needing a persisted, single-use or
 * rotatable token (email verification, invite links, API keys) should
 * use this instead of reimplementing token generation.
 *
 * Hashing is SHA-256 (deterministic), not BCrypt. See RefreshTokenService's
 * original Javadoc for why: validation needs an equality lookup
 * (WHERE token_hash = ?), which a salted hash can't support.
 */
@Component
public class OpaqueTokenGenerator {

  private static final int TOKEN_BYTE_LENGTH = 32; // 256 bits of entropy

  private final SecureRandom secureRandom = new SecureRandom();

  public String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM, unreachable.
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }

}
