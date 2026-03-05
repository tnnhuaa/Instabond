package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user's profile information")
public class UserMeResponse {

    @Schema(description = "MongoDB document ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Unique username", example = "john_doe")
    private String username;

    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @Schema(description = "Display name", example = "John Doe")
    private String full_name;

    @Schema(description = "Avatar image URL", example = "https://cdn.instabond.com/avatars/john.jpg")
    private String avatar_url;

    @Schema(description = "Short bio / about me", example = "Coffee lover ☕")
    private String bio;

    @Schema(description = "Account creation timestamp (UTC)", example = "2024-01-15T08:30:00Z")
    private Instant created_at;
}
