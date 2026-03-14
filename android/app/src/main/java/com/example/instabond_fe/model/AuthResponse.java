package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName(value = "accessToken", alternate = {"access_token"})
    private String accessToken;

    @SerializedName(value = "refreshToken", alternate = {"refresh_token"})
    private String refreshToken;

    @SerializedName(value = "userId", alternate = {"user_id", "id"})
    private String userId;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getUserId() {
        return userId;
    }
}

