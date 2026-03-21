package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class FollowUserResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("relationship_status")
    private String relationshipStatus;

    @SerializedName("is_mutual_follow")
    private boolean isMutualFollow;

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

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public boolean isMutualFollow() {
        return isMutualFollow;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public void setMutualFollow(boolean mutualFollow) {
        isMutualFollow = mutualFollow;
    }

    @SerializedName("is_close_friend")
    private boolean isCloseFriend;

    public boolean isCloseFriend() { return isCloseFriend; }
    public void setCloseFriend(boolean closeFriend) { isCloseFriend = closeFriend; }
}
