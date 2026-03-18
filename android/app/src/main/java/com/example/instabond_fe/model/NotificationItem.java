package com.example.instabond_fe.model;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

public class NotificationItem {

    private final String actor;
    private final String message;
    private final String time;
    private final boolean emphasizeActor;
    @Nullable
    private final String previewUrl;
    @DrawableRes
    private final int iconRes;
    @ColorRes
    private final int iconTintRes;
    private final boolean largeChip;

    public NotificationItem(
            String actor,
            String message,
            String time,
            boolean emphasizeActor,
            @Nullable String previewUrl,
            @DrawableRes int iconRes,
            @ColorRes int iconTintRes,
            boolean largeChip
    ) {
        this.actor = actor;
        this.message = message;
        this.time = time;
        this.emphasizeActor = emphasizeActor;
        this.previewUrl = previewUrl;
        this.iconRes = iconRes;
        this.iconTintRes = iconTintRes;
        this.largeChip = largeChip;
    }

    public String getActor() {
        return actor;
    }

    public String getMessage() {
        return message;
    }

    public String getTime() {
        return time;
    }

    public boolean isEmphasizeActor() {
        return emphasizeActor;
    }

    @Nullable
    public String getPreviewUrl() {
        return previewUrl;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getIconTintRes() {
        return iconTintRes;
    }

    public boolean isLargeChip() {
        return largeChip;
    }
}
