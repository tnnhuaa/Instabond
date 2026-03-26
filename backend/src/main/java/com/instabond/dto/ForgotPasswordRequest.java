package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(
    name = "ForgotPasswordRequest",
    description = "Request payload used to initiate the password reset flow for an existing account."
)
public class ForgotPasswordRequest {
    @Schema(
        description = "Registered account email that should receive the reset OTP.",
        example = "user@example.com",
        format = "email",
        minLength = 6,
        maxLength = 254,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
