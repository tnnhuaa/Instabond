package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for authentication operations (for sign-in just email and password are required, for sign-up all fields except avatar_url are required)")
public class AuthRequest {

    @Size(min = 4, max = 20, message = "username must be between 4 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.]*$", message = "username can only contain letters, numbers, '_' or '.'")
    @Schema(description = "4–20 characters, only letters, numbers, '_' or '.' (required for register only)",
            example = "john_doe")
    private String username;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Schema(description = "Valid email address", example = "john@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "password is required")
    @Schema(description = "User password", example = "secret123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Optional avatar image URL", example = "https://cdn.instabond.com/avatars/john.jpg")
    private String avatar_url;
}
