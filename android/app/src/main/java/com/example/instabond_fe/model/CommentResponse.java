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
}
