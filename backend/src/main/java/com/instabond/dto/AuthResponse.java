package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Authentication response returned after register or login")
public class AuthResponse {

    @Schema(description = "JWT access token — expires in 1 day", example = "eyJhbGci...")
    private String accessToken;

    @Schema(description = "JWT refresh token — expires in 7 days, use POST /api/auth/refresh to renew access token", example = "eyJhbGci...")
    private String refreshToken;
    private String id;
}
