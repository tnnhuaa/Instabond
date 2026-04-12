package com.example.instabond_fe.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class WsEvent {
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_PRESENCE = "PRESENCE";
    public static final String TYPE_ERROR = "ERROR";

    @SerializedName("type")
    private String type;

    @SerializedName("payload")
    private JsonElement payload;

    public String getType() {
        return type;
    }

    public JsonElement getPayload() {
        return payload;
    }
}
