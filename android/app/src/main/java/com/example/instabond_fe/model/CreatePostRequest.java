package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class CreatePostRequest {
    @SerializedName("caption")
    private final String caption;

    @SerializedName("media")
    private final List<MediaRequest> media;

    public CreatePostRequest(String caption, List<MediaRequest> media) {
        this.caption = caption;
        this.media = media;
    }

    public static CreatePostRequest fromCaptionAndMedia(String caption, String mediaUrl, int width, int height) {
        List<MediaRequest> media = new ArrayList<>();
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            media.add(new MediaRequest(mediaUrl, width, height));
        }
        return new CreatePostRequest(caption, media);
    }

    public static class MediaRequest {
        @SerializedName("url")
        private final String url;

        @SerializedName("width")
        private final int width;

        @SerializedName("height")
        private final int height;

        public MediaRequest(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }
}

