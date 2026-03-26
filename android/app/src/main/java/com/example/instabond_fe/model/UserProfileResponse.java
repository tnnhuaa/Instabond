package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class UserProfileResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("bio")
    private String bio;

    @SerializedName(value = "phone_number")
    private String phoneNumber;

    @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar", "profile_picture", "profilePicture"})
    private String avatarUrl;

    @SerializedName("posts_count")
    private int postsCount;

    @SerializedName("followers_count")
    private int followersCount;

    @SerializedName("following_count")
    private int followingCount;

    @SerializedName(value = "is_private", alternate = {"_private", "private", "isPrivate"})
    private boolean isPrivate;

    @SerializedName("relationship_status")
    private String relationshipStatus;
    
    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
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

    public String getBio() {
        return bio;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
    public String getAvatarUrl() {
        return avatarUrl;
    }

    public int getPostsCount() {
        return postsCount;
    }

    public int getFollowersCount() {
        return followersCount;
    }

    public int getFollowingCount() {
        return followingCount;
    }

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }
}

