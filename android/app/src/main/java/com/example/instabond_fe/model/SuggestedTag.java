package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class SuggestedTag {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("is_private")
    private Boolean isPrivate;

    @SerializedName("confidence")
    private Double confidence;

    // Keep this flexible until server position shape is finalized for Android.
    @SerializedName("position")
    private Object position;

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

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Object getPosition() {
        return position;
    }
}

