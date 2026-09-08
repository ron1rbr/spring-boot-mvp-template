package com.example.template.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.template.auth.dto.AuthResponse;
import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.RegisterRequest;
import com.example.template.auth.exception.InvalidCredentialsException;
import com.example.template.common.exception.ConflictException;
import com.example.template.user.User;
import com.example.template.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ConflictException("Email already registered: " + request.email());
    }

    User user = User.register(request.email(), passwordEncoder.encode(request.password()), request.fullName());
    userRepository.save(user);

    return buildAuthResponse(user);
  }

  public AuthResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    } catch (BadCredentialsException ex) {
      // Same exception/message for "wrong password" and "unknown email":
      // see InvalidCredentialsException's Javadoc for why.
      throw new InvalidCredentialsException("Invalid email or password");
    }

    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

    return buildAuthResponse(user);
  }

  private AuthResponse buildAuthResponse(User user) {
    String accessToken = jwtService.generateAccessToken(user);
    return new AuthResponse(accessToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
  }

}
