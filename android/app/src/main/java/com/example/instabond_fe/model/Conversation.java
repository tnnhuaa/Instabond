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
        @SerializedName(value = "id", alternate = {"user_id", "userId"})
        private String id;

        @SerializedName(value = "username", alternate = {"user_name", "userName", "full_name", "fullName"})
        private String username;

        @SerializedName(value = "avatar_url", alternate = {"avatarUrl", "avatar", "profile_picture", "profilePicture"})
        private String avatarUrl;

        @SerializedName(value = "email", alternate = {"mail"})
        private String email;

        @SerializedName(value = "is_online", alternate = {"isOnline", "online"})
        private boolean isOnline;

        @SerializedName(value = "streak_count", alternate = {"streakCount"})
        private int streakCount;

        @SerializedName(value = "has_fired_streak", alternate = {"hasFiredStreak"})
        private boolean hasFiredStreak;

        public String getUsername() { return username; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getId() { return id; }
        public String getEmail() { return email; }
        public boolean isOnline() { return isOnline; }
        public int getStreakCount() { return streakCount; }
        public boolean isHasFiredStreak() { return hasFiredStreak; }
        public void setUsername(String username) { this.username = username; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public void setEmail(String email) { this.email = email; }
        public void setOnline(boolean online) { isOnline = online; }
        public void setStreakCount(int streakCount) { this.streakCount = streakCount; }
        public void setHasFiredStreak(boolean hasFiredStreak) { this.hasFiredStreak = hasFiredStreak; }
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
