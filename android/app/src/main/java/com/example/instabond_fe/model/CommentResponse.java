package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class CommentResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("author")
    private PostResponse.AuthorInfo author;

    @SerializedName("content")
    private String content;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("parent_id")
    private String parentId;

    @SerializedName("likes_count")
    private int likesCount;

    @SerializedName("is_liked")
    private boolean isLiked;

    public String getId() {
        return id;
    }

    public PostResponse.AuthorInfo getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public int getLikesCount() {
        return likesCount;
    }
    
    public boolean isLiked() {
        return isLiked;
    }
    
    public void setLiked(boolean liked) {
        isLiked = liked;
    }
    
    public void setLikesCount(int count) {
        likesCount = count;
    }
}
