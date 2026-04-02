package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class ProfileShareResponse {
    @SerializedName("user_id")
    private String userId;

    @SerializedName("username")
    private String username;

    @SerializedName("qr_code_uid")
    private String qrCodeUid;

    @SerializedName("deep_link")
    private String deepLink;

    @SerializedName("share_text")
    private String shareText;

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getQrCodeUid() {
        return qrCodeUid;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public String getShareText() {
        return shareText;
    }
}