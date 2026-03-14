package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body to create a comment on a post")
public class CreateCommentRequest {

    @Schema(description = "Comment content", example = "Looks great!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "Optional reaction icon", example = "heart")
    private String reaction_icon;
}

