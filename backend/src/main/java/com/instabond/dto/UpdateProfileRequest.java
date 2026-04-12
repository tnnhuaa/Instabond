package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body to update the authenticated user's profile - only include fields you want to change")
public class UpdateProfileRequest {

    @Size(max = 50, message = "full_name must be at most 50 characters")
    @Schema(description = "Display name (max 50 characters)", example = "John Doe")
    private String full_name;

    @Size(max = 150, message = "bio must be at most 150 characters")
    @Schema(description = "Short bio / about me (max 150 characters)", example = "Coffee lover and photography enthusiast")
    private String bio;

    @Size(max = 20, message = "phone_number must be at most 20 characters")
    @Schema(description = "Phone number", example = "0912345678")
    private String phone_number;

    @Schema(description = "Account settings")
    private SettingsRequest settings;

    @Data
    @Schema(description = "Account settings - only include fields you want to change")
    public static class SettingsRequest {
        @Pattern(regexp = "^(everyone|friends|none)$", message = "allow_tagging must be one of: everyone, friends, none")
        @Schema(description = "Who can tag this user: everyone / friends / none", example = "friends")
        private String allow_tagging;

        @Schema(description = "Whether the account is private", example = "false")
        private Boolean is_private;

        @Pattern(regexp = "^(light|dark|system)$", message = "theme must be one of: light, dark, system")
        @Schema(description = "App theme preference: light / dark / system", example = "dark")
        private String theme;
    }
}
