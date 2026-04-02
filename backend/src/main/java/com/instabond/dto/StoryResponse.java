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
@Schema(description = "Story details returned to the client")
public class StoryResponse {

    @Schema(description = "Story ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Author information")
    private AuthorInfo author;

    @Schema(description = "Story media URL", example = "https://res.cloudinary.com/instabond/image/upload/story.jpg")
    private String media_url;

    @Schema(description = "Story type", allowableValues = {"image"}, example = "image")
    private String type;

    @Schema(description = "Story creation timestamp (UTC)", example = "2024-01-15T08:30:00Z")
    private Instant created_at;

    @Schema(description = "Story expiry timestamp (UTC)", example = "2024-01-16T08:30:00Z")
    private Instant expires_at;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Compact author info embedded inside a story")
    public static class AuthorInfo {
        @Schema(description = "Author's user ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
        private String id;

        @Schema(description = "Author's username", example = "john_doe")
        private String username;

        @Schema(description = "Author's display name", example = "John Doe")
        private String full_name;

        @Schema(description = "Author's avatar URL", example = "https://res.cloudinary.com/instabond/image/upload/avatar.jpg")
        private String avatar_url;

        @Schema(description = "Intimacy score with the caller", example = "300")
        private Integer intimacy_score;
    }
}
