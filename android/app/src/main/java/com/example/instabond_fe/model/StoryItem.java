package com.example.instabond_fe.model;

import java.io.Serializable;

public class StoryItem implements Serializable {
    private final String id;
    private final String authorId;
    private final String username;
    private final String avatarUrl;
    private final String mediaUrl;
    private final String createdAt;
    private final boolean createCard;

    public StoryItem(String id,
                     String authorId,
                     String username,
                     String avatarUrl,
                     String mediaUrl,
                     String createdAt,
                     boolean createCard) {
        this.id = id;
        this.authorId = authorId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.mediaUrl = mediaUrl;
        this.createdAt = createdAt;
        this.createCard = createCard;
    }

    public String getId() {
        return id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isCreateCard() {
        return createCard;
    }

    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.trim().isEmpty();
    }
}
