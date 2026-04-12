package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class AuthRequest {
    @SerializedName("username")
    private final String username;

    @SerializedName("email")
    private final String email;

    @SerializedName("password")
    private final String password;

    @SerializedName("avatar_url")
    private final String avatarUrl;

    private AuthRequest(String username, String email, String password, String avatarUrl) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.avatarUrl = avatarUrl;
    }

    public static AuthRequest forLogin(String email, String password) {
        return new AuthRequest(null, email, password, null);
    }

    public static AuthRequest forRegister(String username, String email, String password, String avatarUrl) {
        return new AuthRequest(username, email, password, avatarUrl);
    }
}

