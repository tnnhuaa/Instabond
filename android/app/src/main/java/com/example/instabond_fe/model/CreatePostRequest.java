package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class CreatePostRequest {
    @SerializedName("caption")
    private final String caption;

    @SerializedName("media")
    private final List<MediaRequest> media;

    @SerializedName("location")
    private final LocationRequest location;

    @SerializedName("music_suggestion")
    private final MusicSuggestionRequest musicSuggestion;

    @SerializedName("tagged_users")
    private final List<TaggedUserRequest> taggedUsers;

    public CreatePostRequest(String caption,
                             List<MediaRequest> media,
                             LocationRequest location,
                             MusicSuggestionRequest musicSuggestion,
                             List<TaggedUserRequest> taggedUsers) {
        this.caption = caption;
        this.media = media;
        this.location = location;
        this.musicSuggestion = musicSuggestion;
        this.taggedUsers = taggedUsers;
    }

    public static CreatePostRequest fromCaptionAndMedia(String caption,
                                                        String mediaUrl,
                                                        int width,
                                                        int height,
                                                        List<TaggedUserRequest> taggedUsers,
                                                        LocationRequest location,
                                                        MusicSuggestionRequest musicSuggestion) {
        List<MediaRequest> media = new ArrayList<>();
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            media.add(new MediaRequest(mediaUrl, width, height));
        }

        if (taggedUsers == null) {
            taggedUsers = new ArrayList<>();
        }

        return new CreatePostRequest(caption, media, location, musicSuggestion, taggedUsers);
    }

    public static class MediaRequest {
        @SerializedName("url")
        private final String url;

        @SerializedName("width")
        private final int width;

        @SerializedName("height")
        private final int height;

        public MediaRequest(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    public static class TaggedUserRequest {
        @SerializedName("user_id")
        private final String userId;

        @SerializedName("tag_type")
        private final String tagType;

        @SerializedName("confidence")
        private final Double confidence;

        @SerializedName("position")
        private final Position position;

        public TaggedUserRequest(String userId, String tagType, Double confidence, Position position) {
            this.userId = userId;
            this.tagType = tagType;
            this.confidence = confidence;
            this.position = position;
        }

        public static class Position {
            @SerializedName("x")
            private final Double x;

            @SerializedName("y")
            private final Double y;

            public Position(Double x, Double y) {
                this.x = x;
                this.y = y;
            }
        }
    }

    public static class LocationRequest {
        @SerializedName("name")
        private final String name;

        @SerializedName("coordinates")
        private final List<Double> coordinates;

        public LocationRequest(String name, List<Double> coordinates) {
            this.name = name;
            this.coordinates = coordinates;
        }
    }

    public static class MusicSuggestionRequest {
        @SerializedName("song_name")
        private final String songName;

        @SerializedName("artist")
        private final String artist;

        @SerializedName("preview_url")
        private final String previewUrl;

        public MusicSuggestionRequest(String songName, String artist, String previewUrl) {
            this.songName = songName;
            this.artist = artist;
            this.previewUrl = previewUrl;
        }
    }
}