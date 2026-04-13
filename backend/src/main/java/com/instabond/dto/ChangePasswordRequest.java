package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(
    name = "ChangePasswordRequest",
    description = "Request payload for changing password while authenticated."
)
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(description = "Current account password", example = "OldPass@123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Schema(description = "New password", example = "NewPass@123", minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Schema(description = "Confirm new password", example = "NewPass@123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmNewPassword;
}