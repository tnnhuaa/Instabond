package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body to update the authenticated user's profile — only include fields you want to change")
public class UpdateProfileRequest {

    @Schema(description = "Display name (max 50 characters)", example = "John Doe")
    private String full_name;

    @Schema(description = "Short bio / about me (max 150 characters)", example = "Coffee lover and photography enthusiast ☕")
    private String bio;

    @Schema(description = "Phone number", example = "0912345678")
    private String phone_number;

    @Schema(description = "Account settings")
    private SettingsRequest settings;

    @Data
    @Schema(description = "Account settings — only include fields you want to change")
    public static class SettingsRequest {
        @Schema(description = "Who can tag this user: everyone / friends / none", example = "friends")
        private String allow_tagging;

        @Schema(description = "Whether the account is private", example = "false")
        private Boolean is_private;

        @Schema(description = "App theme preference: light / dark / system", example = "dark")
        private String theme;
    }
}
