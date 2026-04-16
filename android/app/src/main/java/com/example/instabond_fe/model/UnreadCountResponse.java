package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;
public class UnreadCountResponse {
    @SerializedName("unread_count")
    private long unreadCount;

    public long getUnread_count() {
        return unreadCount;
    }
}
