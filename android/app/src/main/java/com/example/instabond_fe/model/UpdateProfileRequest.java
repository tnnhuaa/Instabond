package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class UpdateProfileRequest {
    @SerializedName("full_name")
    private String fullName;

    @SerializedName("bio")
    private String bio;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("settings")
    private SettingsRequest settings;

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setSettings(SettingsRequest settings) {
        this.settings = settings;
    }

    public static class SettingsRequest {
        @SerializedName("allow_tagging")
        private String allowTagging;

        @SerializedName("is_private")
        private Boolean isPrivate;

        @SerializedName("theme")
        private String theme;

        public void setAllowTagging(String allowTagging) {
            this.allowTagging = allowTagging;
        }

        public void setIsPrivate(Boolean isPrivate) {
            this.isPrivate = isPrivate;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }
    }
}
