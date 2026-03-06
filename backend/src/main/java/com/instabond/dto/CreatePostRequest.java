package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request body to create a new post")
public class CreatePostRequest {

    @Schema(description = "Post caption (max 2200 characters)", example = "Sunset at the beach 🌅")
    private String caption;

    @Schema(description = "Location tag attached to the post")
    private LocationRequest location;

    @Schema(description = "Music suggestion attached to the post")
    private MusicSuggestionRequest music_suggestion;

    @Schema(description = "List of media items with pre-uploaded URLs")
    private List<MediaRequest> media;

    @Schema(description = "List of users tagged in the post")
    private List<TaggedUserRequest> tagged_users;

    @Data
    @Schema(description = "Media item with a pre-uploaded URL")
    public static class MediaRequest {
        @Schema(example = "https://res.cloudinary.com/instabond/image/upload/photo.jpg")
        private String url;
        @Schema(example = "1080")
        private int width;
        @Schema(example = "1350")
        private int height;
    }

    @Data
    @Schema(description = "Location tag")
    public static class LocationRequest {
        @Schema(example = "Hanoi, Vietnam")
        private String name;
        @Schema(description = "[longitude, latitude]", example = "[105.8412, 21.0245]")
        private List<Double> coordinates;
    }

    @Data
    @Schema(description = "Music suggestion")
    public static class MusicSuggestionRequest {
        @Schema(example = "Blinding Lights")
        private String song_name;
        @Schema(example = "The Weeknd")
        private String artist;
        @Schema(example = "https://cdn.example.com/preview.mp3")
        private String preview_url;
    }

    @Data
    @Schema(description = "Tagged user in the post")
    public static class TaggedUserRequest {
        @Schema(description = "ID of the tagged user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
        private String user_id;
    }
}
