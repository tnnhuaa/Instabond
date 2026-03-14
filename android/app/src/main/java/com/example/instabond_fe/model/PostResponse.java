package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("author")
    private AuthorInfo author;

    @SerializedName("caption")
    private String caption;

    @SerializedName("media")
    private List<MediaItem> media;

    @SerializedName("music_suggestion")
    private Object musicSuggestion;

    @SerializedName("stats")
    private Stats stats;

    public String getId() {
        return id;
    }

    public AuthorInfo getAuthor() {
        return author;
    }

    public String getCaption() {
        return caption;
    }

    public List<MediaItem> getMedia() {
        return media;
    }

    public boolean hasMusicSuggestion() {
        return musicSuggestion != null;
    }

    public Stats getStats() {
        return stats;
    }

    public static class AuthorInfo {
        @SerializedName("username")
        private String username;

        @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar"})
        private String avatarUrl;

        public String getUsername() {
            return username;
        }

        public String getAvatarUrl() {
            return avatarUrl;
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
}
