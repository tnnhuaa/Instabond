package com.example.instabond_fe.model;

import java.io.Serializable;

public class TaggedUserLocal implements Serializable {
    private final String userId;
    private final String username;
    private final String fullName;
    private final String avatarUrl;
    private final float x;
    private final float y;

    public TaggedUserLocal(String userId, String username, String fullName, String avatarUrl, float x, float y) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.x = x;
        this.y = y;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getAvatarUrl() { return avatarUrl; }
    public float getX() { return x; }
    public float getY() { return y; }
}