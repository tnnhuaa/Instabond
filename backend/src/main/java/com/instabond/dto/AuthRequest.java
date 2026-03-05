package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body for authentication operations (for sign-in just email and password are required, for sign-up all fields except avatar_url are required)")
public class AuthRequest {

    @Schema(description = "4–20 characters, only letters, numbers, '_' or '.' (required for register only)",
            example = "john_doe")
    private String username;

    @Schema(description = "Valid email address", example = "john@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "User password", example = "secret123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Optional avatar image URL", example = "https://cdn.instabond.com/avatars/john.jpg")
    private String avatar_url;
}
