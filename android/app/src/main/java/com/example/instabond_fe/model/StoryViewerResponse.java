package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class StoryViewerResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName(value = "full_name", alternate = {"fullName"})
    private String fullName;

    @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar"})
    private String avatarUrl;

    @SerializedName(value = "viewed_at", alternate = {"viewedAt"})
    private String viewedAt;

    @SerializedName("liked")
    private boolean liked;

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

    public String getViewedAt() {
        return viewedAt;
    }

    public boolean isLiked() {
        return liked;
    }
}
