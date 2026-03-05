package com.instabond.controller;

import com.instabond.dto.AuthRequest;
import com.instabond.dto.AuthResponse;
import com.instabond.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            if (request.getUsername() == null || request.getUsername().isBlank()) {
                return ResponseEntity.badRequest().body("Username is required!");
            }
            if (!request.getUsername().matches("^[a-zA-Z0-9_.]{4,20}$")) {
                return ResponseEntity.badRequest().body("Username must be 4–20 characters and only contain letters, numbers, '_' or '.'");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Email is required!");
            }
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body("Password is required!");
            }
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
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
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body("Refresh token is missing!");
            }
            AuthResponse response = authService.refresh(authHeader.substring(7));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired refresh token!");
        }
    }
}