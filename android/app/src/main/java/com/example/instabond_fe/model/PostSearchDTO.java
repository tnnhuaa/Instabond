package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class PostSearchDTO {
    private String id;

    @SerializedName("author_id")
    private String authorId;

    private String caption;

    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    private int likes;
    private int comments;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }
    public int getComments() { return comments; }
    public void setComments(int comments) { this.comments = comments; }
}