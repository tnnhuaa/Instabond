package com.instabond.dto;

import com.instabond.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full post details returned to the client")
public class PostResponse {

    @Schema(description = "Post ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Author information")
    private AuthorInfo author;

    @Schema(description = "Post caption", example = "Sunset at the beach 🌅")
    private String caption;

    @Schema(description = "Location tag attached to this post")
    private Post.Location location;

    @Schema(description = "List of uploaded media items (images or videos)")
    private List<Post.Media> media;

    @Schema(description = "Music suggestion attached to this post")
    private Post.MusicSuggestion music_suggestion;

    @Schema(description = "List of users tagged in this post")
    private List<Post.TaggedUser> tagged_users;

    @Schema(description = "Engagement stats: likes, comments, shares")
    private Post.Stats stats;

    @Schema(description = "Post creation timestamp (UTC)", example = "2024-01-15T08:30:00Z")
    private Instant created_at;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Compact author info embedded inside a post")
    public static class AuthorInfo {
        @Schema(description = "Author's user ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
        private String id;

        @Schema(description = "Author's username", example = "john_doe")
        private String username;

        @Schema(description = "Author's display name", example = "John Doe")
        private String full_name;

        @Schema(description = "Author's avatar URL", example = "https://res.cloudinary.com/instabond/image/upload/avatar.jpg")
        private String avatar_url;
    }
}
