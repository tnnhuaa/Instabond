package com.example.instabond_fe.utils;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;

public final class AvatarLoader {

    private AvatarLoader() {
    }

    public static void load(ImageView imageView, @Nullable String avatarUrl) {
        loadCircle(imageView, avatarUrl, R.drawable.profile_placeholder_bg);
    }

    public static void loadCircle(
            ImageView imageView,
            @Nullable String avatarUrl,
            @DrawableRes int placeholderRes
    ) {
        String trimmedUrl = avatarUrl == null ? null : avatarUrl.trim();
        String model = (trimmedUrl == null || trimmedUrl.isEmpty()) ? null : trimmedUrl;

        Glide.with(imageView)
                .load(model)
                .circleCrop()
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .fallback(placeholderRes)
                .into(imageView);
    }
}
