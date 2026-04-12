package com.example.instabond_fe.model;

public class SearchHistoryDTO {
    private String id;
    private String type; // "TEXT" | "PROFILE"
    private String keyword;
    private String targetUserId;
    private String targetUsername;
    private String targetFullName;
    private String targetAvatarUrl;

    // Getters and Setters
    public String getId() { return id; }
    public String getType() { return type; }
    public String getKeyword() { return keyword; }
    public String getTargetUserId() { return targetUserId; }
    public String getTargetUsername() { return targetUsername; }
    public String getTargetFullName() { return targetFullName; }
    public String getTargetAvatarUrl() { return targetAvatarUrl; }
}