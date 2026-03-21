package com.instabond.service;

import com.instabond.dto.AuthRequest;
import com.instabond.dto.AuthResponse;
import com.instabond.entity.User;
import com.instabond.repository.UserRepository;
import com.instabond.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("The email address has already been used!");
        }

        String username = request.getUsername().toLowerCase();

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("The username has already been used!");
        }

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .avatar_url(request.getAvatar_url())
                .created_at(Instant.now())
                .build();

        user = userRepository.save(user);

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        // Check pass, if incorrect => throw err
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .build();
    }

    public AuthResponse refresh(String refreshToken) {
        String email = jwtUtil.extractEmail(refreshToken);
        if (email == null || !jwtUtil.isTokenValid(refreshToken, email)) {
            throw new IllegalArgumentException("Invalid or expired refresh token!");
        }

        User user = userRepository.findByEmail(email).orElseThrow();

        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .build();
    }
}