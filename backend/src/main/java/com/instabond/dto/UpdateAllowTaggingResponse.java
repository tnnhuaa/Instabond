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
@Schema(description = "Response after updating allow_tagging setting")
public class UpdateAllowTaggingResponse {

    @Schema(description = "Who can tag this user", example = "everyone", allowableValues = {"everyone", "none"})
    private String allow_tagging;
}

