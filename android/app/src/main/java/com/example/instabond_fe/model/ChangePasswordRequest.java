package com.example.instabond_fe.model;

public class ChangePasswordRequest {
    private final String currentPassword;
    private final String newPassword;
    private final String confirmNewPassword;

    public ChangePasswordRequest(String currentPassword, String newPassword, String confirmNewPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmNewPassword = confirmNewPassword;
    }
}