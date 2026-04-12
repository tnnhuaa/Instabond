package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(
    name = "ResetPasswordRequest",
    description = "Request payload for validating a reset OTP and setting a new password for the account."
)
public class ResetPasswordRequest {
    @Schema(
        description = "Email address tied to the reset request.",
        example = "user@example.com",
        format = "email",
        minLength = 6,
        maxLength = 254,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(
        description = "One-time password received by email for verification.",
        example = "123456",
        pattern = "^[0-9]{6}$",
        minLength = 6,
        maxLength = 6,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "OTP is required")
    private String otp;

    @Schema(
        description = "New account password after OTP verification.",
        example = "SecurePass@123",
        minLength = 8,
        maxLength = 72,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "New password is required")
    private String newPassword;
}
