package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SuggestedTag implements Serializable {
    private static final long serialVersionUID = 1L;

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

    @SerializedName("tag_type")
    private String tagType;

    // Keep this flexible until server position shape is finalized for Android.
    @SerializedName("position")
    private Position position;

    public SuggestedTag(String id, String username, String fullName, String avatarUrl, Double x, Double y) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.position = new Position(x, y);
        this.tagType = "user-tag";
        this.confidence = 1.0;
    }

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

    public Position getPosition() {
        return position;
    }

    public String getTagType() {
        return tagType;
    }

    public void setTagType(String tagType) {
        this.tagType = tagType;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public static class Position implements Serializable {
        private static final long serialVersionUID = 1L;

        @SerializedName("x")
        private Double x;

        @SerializedName("y")
        private Double y;

        public Position(Double x, Double y) {
            this.x = x;
            this.y = y;
        }

        public Double getX() { return x; }
        public Double getY() { return y; }
    }
}