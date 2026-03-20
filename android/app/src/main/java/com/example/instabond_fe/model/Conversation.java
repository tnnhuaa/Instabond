package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Conversation {
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("participants")
    private List<Participant> participants;

    @SerializedName("last_message")
    private LastMessage lastMessage;

    @SerializedName("theme")
    private String theme;

    @SerializedName("updated_at")
    private String updatedAt;

    // INNER CLASS FOR PARTICIPANTS
    public static class Participant {
        @SerializedName("id")
        private String id;

        @SerializedName("username")
        private String username;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getUsername() { return username; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getId() { return id; }
    }

    public Conversation() {
    }

    public Conversation(String id, List<Participant> participants, LastMessage lastMessage, String theme, String updatedAt) {
        this.id = id;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.theme = theme;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public LastMessage getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(LastMessage lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
