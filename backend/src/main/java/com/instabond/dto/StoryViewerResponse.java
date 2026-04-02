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
@Schema(description = "Viewer details for a story owned by the authenticated user")
public class StoryViewerResponse {

    @Schema(description = "Viewer user id", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Viewer username", example = "john_doe")
    private String username;

    @Schema(description = "Viewer display name", example = "John Doe")
    private String full_name;

    @Schema(description = "Viewer avatar URL", example = "https://res.cloudinary.com/instabond/image/upload/avatar.jpg")
    private String avatar_url;

    @Schema(description = "When the viewer watched the story", example = "2024-01-15T08:35:00Z")
    private Instant viewed_at;

    @Schema(description = "Whether this viewer hearted the story", example = "true")
    private boolean liked;
}
