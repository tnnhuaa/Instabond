package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("is_liked")
    private boolean isLiked;

    @SerializedName("is_bookmarked")
    private boolean isBookmarked;

    @SerializedName("author")
    private AuthorInfo author;

    @SerializedName("caption")
    private String caption;

    @SerializedName("location")
    private Location location;

    @SerializedName("media")
    private List<MediaItem> media;

    @SerializedName(value = "music_suggestion", alternate = {"musicSuggestion"})
    private MusicSuggestion musicSuggestion;

    @SerializedName("stats")
    private Stats stats;

    @SerializedName(value = "created_at", alternate = {"createdAt"})
    private String createdAt;

    public String getId() {
        return id;
    }

    public boolean isLiked() {
        return isLiked;
    }

    public boolean isBookmarked() {
        return isBookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        isBookmarked = bookmarked;
    }

    public AuthorInfo getAuthor() {
        return author;
    }

    public String getCaption() {
        return caption;
    }

    public Location getLocation() {
        return location;
    }

    public String getLocationName() {
        return location == null ? "" : valueOrEmpty(location.getName());
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public boolean hasMusicSuggestion() {
        return !getMusicDisplayText().isEmpty();
    }

    public MusicSuggestion getMusicSuggestion() {
        return musicSuggestion;
    }

    public String getMusicDisplayText() {
        if (musicSuggestion == null) {
            return "";
        }

        String songName = valueOrEmpty(musicSuggestion.getSongName());
        if (!songName.isEmpty()) {
            return songName;
        }

        return valueOrEmpty(musicSuggestion.getArtist());
    }

    public String getMusicPreviewUrl() {
        if (musicSuggestion == null) {
            return "";
        }
        return valueOrEmpty(musicSuggestion.getPreviewUrl());
    }

    public Stats getStats() {
        return stats;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public static class AuthorInfo {
        @SerializedName("id")
        private String id;

        @SerializedName("username")
        private String username;

        @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar"})
        private String avatarUrl;

        public String getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }

    public static class Location {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class MediaItem {
        @SerializedName(value = "url", alternate = {"media_url", "secure_url", "image_url"})
        private String url;

        public String getUrl() {
            return url;
        }
    }

    public static class Stats {
        @SerializedName(value = "likes", alternate = {"likes_count"})
        private int likes;

        @SerializedName(value = "comments", alternate = {"comments_count"})
        private int comments;

        @SerializedName(value = "shares", alternate = {"shares_count"})
        private int shares;

        public int getLikes() {
            return likes;
        }

        public int getComments() {
            return comments;
        }

        public int getShares() {
            return shares;
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
