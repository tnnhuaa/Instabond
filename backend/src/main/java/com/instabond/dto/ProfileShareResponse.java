package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Share metadata for profile QR/deep-link flow")
public class ProfileShareResponse {

    @Schema(description = "Target user id", example = "65b111111111111111111111")
    private String user_id;

    @Schema(description = "Target username", example = "nam_nguyen")
    private String username;

    @Schema(description = "Stable QR identifier", example = "qr_65b111111111111111111111")
    private String qr_code_uid;

    @Schema(description = "App deep-link to open this profile", example = "instabond://profile?uid=qr_65b111111111111111111111")
    private String deep_link;

    @Schema(description = "Human readable share text", example = "Check out @nam_nguyen on Instabond: instabond://profile?uid=qr_65b111111111111111111111")
    private String share_text;
}

