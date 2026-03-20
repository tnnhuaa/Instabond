package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class LastMessage {
    @SerializedName("content")
    private String content;

    @SerializedName("sender_id")
    private String senderId;

    @SerializedName("sent_at")
    private String sentAt;

    @SerializedName("is_read")
    private Boolean isRead;

    public LastMessage() {
    }

    public LastMessage(String content, String senderId, String sentAt, Boolean isRead) {
        this.content = content;
        this.senderId = senderId;
        this.sentAt = sentAt;
        this.isRead = isRead;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSentAt() {
        return sentAt;
    }

    public void setSentAt(String sentAt) {
        this.sentAt = sentAt;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }
}
