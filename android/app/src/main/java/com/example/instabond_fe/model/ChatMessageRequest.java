package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class ChatMessageRequest {
    @SerializedName(value = "conversation_id", alternate = {"conversationId"})
    private String conversationId;

    @SerializedName("content")
    private String content;

    @SerializedName("type")
    private String type;

    public ChatMessageRequest() {
    }

    public ChatMessageRequest(String conversationId, String content, String type) {
        this.conversationId = conversationId;
        this.content = content;
        this.type = type;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
