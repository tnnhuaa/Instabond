package com.instabond.dto;

import com.instabond.enums.SearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(
        name = "SearchHistoryRequest",
        description = "Payload to save search history. Use keyword when type=TEXT, or targetUserId when type=PROFILE."
)
public class SearchHistoryRequest {

    @Schema(
            description = "Search history type",
            example = "TEXT",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"TEXT", "PROFILE"}
    )
    private SearchType type;

    @Schema(description = "Search keyword for TEXT type", example = "penaldo")
    private String keyword;

    @Schema(description = "Target user id for PROFILE type", example = "67fb1f1b9d5a6f25a1c6d3b0")
    private String targetUserId;
}