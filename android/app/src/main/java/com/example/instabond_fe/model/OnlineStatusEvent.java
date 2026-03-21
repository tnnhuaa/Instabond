package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class OnlineStatusEvent {
    @SerializedName(value = "email", alternate = {"user_email", "userEmail"})
    private String email;

    @SerializedName(value = "user_id", alternate = {"userId", "id"})
    private String userId;

    @SerializedName(value = "is_online", alternate = {"online"})
    private Boolean isOnline;

    @SerializedName(value = "status", alternate = {"state", "event"})
    private String status;

    public String getEmail() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isOnline() {
        if (isOnline != null) {
            return isOnline;
        }
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "ONLINE".equals(normalized) || "CONNECTED".equals(normalized) || "JOIN".equals(normalized);
    }
}
