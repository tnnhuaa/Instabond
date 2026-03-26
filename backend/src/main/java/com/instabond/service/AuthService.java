package com.instabond.service;

import com.instabond.dto.AuthRequest;
import com.instabond.dto.AuthResponse;
import com.instabond.dto.ForgotPasswordRequest;
import com.instabond.dto.ResetPasswordRequest;
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
    private final OtpService otpService;
    private final EmailService emailService;

    public AuthResponse register(AuthRequest request) {
        validateRegisterRequest(request);

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
        validateLoginRequest(request);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new com.instabond.exception.ResourceNotFoundException("User not found: " + request.getEmail()));

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .build();
    }

    public AuthResponse refresh(String refreshToken) {
        validateRefreshTokenInput(refreshToken);

        String email = jwtUtil.extractEmail(refreshToken);
        if (email == null || !jwtUtil.isTokenValid(refreshToken, email)) {
            throw new IllegalArgumentException("Invalid or expired refresh token!");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.instabond.exception.ResourceNotFoundException("User not found: " + email));

        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .build();
    }

    // FORGOT PASSWORD FLOW: Check mail -> Send OTP
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new com.instabond.exception.ResourceNotFoundException("Email not found: " + request.getEmail()));

        // Save OTP to Redis and send email
        String otpCode = otpService.generateAndSaveOtp(user.getEmail());
        emailService.sendOtpEmail(user.getEmail(), otpCode);
    }

    // RESET PASSWORD FLOW: Verify OTP -> Update password
    public void resetPassword(ResetPasswordRequest request) {
        boolean isValidOtp = otpService.verifyOtp(request.getEmail(), request.getOtp());
        if (!isValidOtp) {
            throw new IllegalArgumentException("Invalid or expired OTP!");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new com.instabond.exception.ResourceNotFoundException("User not found!"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private void validateRegisterRequest(AuthRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required!");
        }
        if (!request.getUsername().matches("^[a-zA-Z0-9_.]{4,20}$")) {
            throw new IllegalArgumentException("Username must be 4-20 characters and only contain letters, numbers, '_' or '.'");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required!");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required!");
        }
    }

    private void validateLoginRequest(AuthRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required!");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required!");
        }
    }

    private void validateRefreshTokenInput(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is missing!");
        }
    }
}