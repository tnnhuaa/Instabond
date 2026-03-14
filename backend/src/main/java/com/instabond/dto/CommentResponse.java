package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Comment details")
public class CommentResponse {

    @Schema(description = "Interaction ID for this comment", example = "65b999999999999999999991")
    private String id;

    @Schema(description = "Post ID that receives this comment", example = "65b444444444444444444441")
    private String post_id;

    @Schema(description = "Comment text", example = "Amazing shot")
    private String content;

    @Schema(description = "Optional reaction icon", example = "heart")
    private String reaction_icon;

    @Schema(description = "Comment author information")
    private AuthorInfo author;

    @Schema(description = "Comment creation timestamp (UTC)", example = "2026-03-13T03:15:00Z")
    private Instant created_at;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Compact author info in a comment")
    public static class AuthorInfo {
        @Schema(description = "Author user ID", example = "65b111111111111111111111")
        private String id;

        @Schema(description = "Author username", example = "nam_nguyen")
        private String username;

        @Schema(description = "Author display name", example = "Nam Nguyen")
        private String full_name;

        @Schema(description = "Author avatar URL", example = "https://img.url/avatar1.jpg")
        private String avatar_url;
    }
}

