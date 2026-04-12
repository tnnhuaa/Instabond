package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class CreateCommentRequest {
    @SerializedName("content")
    private String content;

    @SerializedName("parent_id")
    private String parentId;

    public CreateCommentRequest(String content) {
        this.content = content;
    }
    
    public CreateCommentRequest(String content, String parentId) {
        this.content = content;
        this.parentId = parentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
