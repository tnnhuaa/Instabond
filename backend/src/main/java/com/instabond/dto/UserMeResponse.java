package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import java.util.List;
import com.instabond.entity.User;

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

    @Schema(description = "Phone number", example = "0912345678")
    private String phone_number;

    @Schema(description = "Display name", example = "John Doe")
    private String full_name;

    @Schema(description = "Avatar image URL", example = "https://cdn.instabond.com/avatars/john.jpg")
    private String avatar_url;

    @Schema(description = "Short bio / about me", example = "Coffee lover ☕")
    private String bio;

    @Schema(description = "Total number of posts published by this user", example = "42")
    private long posts_count;

    @Schema(description = "Number of followers", example = "1200")
    private long followers_count;

    @Schema(description = "Number of users this account is following", example = "300")
    private long following_count;

    @Schema(description = "Whether this account is private", example = "false")
    private boolean is_private;

    @Schema(description = "Badges earned by this user")
    private List<User.Badge> badges;

    @Schema(description = "Account settings (tagging permission, theme, etc.)")
    private User.Setting settings;

    @Schema(description = "Account creation timestamp (UTC)", example = "2024-01-15T08:30:00Z")
    private Instant created_at;
}
