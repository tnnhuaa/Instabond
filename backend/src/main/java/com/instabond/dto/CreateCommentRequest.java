package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body to create a comment on a post")
public class CreateCommentRequest {

    @NotBlank(message = "content is required")
    @Schema(description = "Comment content", example = "Looks great!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "Optional reaction icon", example = "heart")
    private String reaction_icon;

    @Schema(description = "Optional parent comment ID for replies", example = "65b999999999999999999991")
    private String parent_id;
}
