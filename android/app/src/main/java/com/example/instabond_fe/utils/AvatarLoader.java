package com.example.instabond_fe.utils;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;

public final class AvatarLoader {

    private AvatarLoader() {
    }

    public static void load(ImageView imageView, @Nullable String avatarUrl) {
        String trimmedUrl = avatarUrl == null ? null : avatarUrl.trim();
        String model = (trimmedUrl == null || trimmedUrl.isEmpty()) ? null : trimmedUrl;

        Glide.with(imageView)
                .load(model)
                .circleCrop()
                .placeholder(R.drawable.profile_placeholder_bg)
                .error(R.drawable.profile_placeholder_bg)
                .fallback(R.drawable.profile_placeholder_bg)
                .into(imageView);
    }
}
