package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class StoryResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("author")
    private AuthorInfo author;

    @SerializedName(value = "media_url", alternate = {"mediaUrl", "image_url"})
    private String mediaUrl;

    @SerializedName("type")
    private String type;

    @SerializedName(value = "created_at", alternate = {"createdAt"})
    private String createdAt;

    @SerializedName(value = "expires_at", alternate = {"expiresAt"})
    private String expiresAt;

    @SerializedName(value = "viewed_by_me", alternate = {"viewedByMe"})
    private boolean viewedByMe;

    @SerializedName(value = "liked_by_me", alternate = {"likedByMe"})
    private boolean likedByMe;

    @SerializedName(value = "viewer_count", alternate = {"viewerCount"})
    private int viewerCount;

    public String getId() {
        return id;
    }

    public AuthorInfo getAuthor() {
        return author;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getType() {
        return type;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public boolean isViewedByMe() {
        return viewedByMe;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public int getViewerCount() {
        return viewerCount;
    }

    public static class AuthorInfo {
        @SerializedName("id")
        private String id;

        @SerializedName("username")
        private String username;

        @SerializedName(value = "full_name", alternate = {"fullName"})
        private String fullName;

        @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar"})
        private String avatarUrl;

        public String getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getFullName() {
            return fullName;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }
}
