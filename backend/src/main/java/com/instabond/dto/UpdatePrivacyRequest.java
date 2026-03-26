package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request body to change account privacy mode")
public class UpdatePrivacyRequest {

    @NotNull(message = "is_private is required")
    @Schema(description = "Set account private/public", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean is_private;
}
