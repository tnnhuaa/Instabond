package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class Notification {
    private String id;
    @SerializedName("recipient_id")
    private String recipientId;
    @SerializedName("sender_id")
    private String senderId;
    private String type;
    private String content;
    @SerializedName("is_read")
    private boolean isRead;
    private Map<String, String> metadata;
    @SerializedName("created_at")
    private String createdAt;


    public String getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public String getSenderId() { return senderId; }
    public String getType() { return type; }
    public String getContent() { return content; }

    public boolean isRead() { return isRead; }
    public Map<String, String> getMetadata() { return metadata; }
    public String getCreatedAt() { return createdAt; }
    public void setRead(boolean read) { isRead = read; }

    public String getPostId() {
        return metadata != null ? metadata.get("post_id") : null;
    }
}
