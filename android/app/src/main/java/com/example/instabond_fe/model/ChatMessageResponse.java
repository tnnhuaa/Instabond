package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class ChatMessageResponse {
    @SerializedName(value = "id", alternate = {"_id"})
    private String id;

    @SerializedName(value = "conversation_id", alternate = {"conversationId"})
    private String conversationId;

    @SerializedName(value = "sender_id", alternate = {"senderId"})
    private String senderId;

    private String content;
    private String type;

    @SerializedName(value = "created_at", alternate = {"createdAt"})
    private String createdAt;

    public ChatMessageResponse() {
    }

    public ChatMessageResponse(String id, String conversationId, String senderId, String type, String content, String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
