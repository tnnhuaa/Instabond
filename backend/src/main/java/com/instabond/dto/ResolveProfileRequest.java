package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload used to resolve a profile from QR/deep-link/share text")
public class ResolveProfileRequest {

    @NotBlank(message = "payload is required")
    @Schema(
            description = "Raw payload from QR/deep-link/share text",
            example = "instabond://profile?uid=qr_abc123"
    )
    private String payload;
}

