package com.instabond.controller;

import com.instabond.dto.AuthRequest;
import com.instabond.dto.AuthResponse;
import com.instabond.dto.ForgotPasswordRequest;
import com.instabond.dto.ResetPasswordRequest;
import com.instabond.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login and refresh token")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a new account",
            description = "Create a new user with username, email and password. Avatar URL is optional."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful — returns accessToken & refreshToken"),
            @ApiResponse(responseCode = "400", description = "Validation failed or email/username already exists")
    })
    @SecurityRequirements   // public route — no token needed
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(
            summary = "Login",
            description = "Authenticate with email and password. Returns accessToken (1 day) and refreshToken (7 days)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful — returns accessToken & refreshToken"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    @SecurityRequirements   // public route — no token needed
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Refresh access token",
            description = "Exchange a valid refreshToken for a new accessToken. Pass the refreshToken as `Authorization: Bearer <refreshToken>`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Returns new accessToken, old refreshToken is kept"),
            @ApiResponse(responseCode = "400", description = "Missing Authorization header"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired")
    })
    @SecurityRequirements   // uses refresh token, not access token
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Refresh token is missing!");
        }

        AuthResponse response = authService.refresh(authHeader.substring(7));
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Forgot Password",
            description = "Send a 6-digit OTP to the user's email if it exists in the system."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
            @ApiResponse(responseCode = "404", description = "Email not found")
    })
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    @Operation(
            summary = "Reset Password",
            description = "Verify the OTP and set a new password for the user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password has been reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
    })
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password has been reset successfully.");
    }
}