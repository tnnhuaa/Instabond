package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class CreatePostRequest {
    @SerializedName("caption")
    private final String caption;

    @SerializedName("media")
    private final List<MediaRequest> media;

    @SerializedName("tagged_users")
    private final List<TaggedUserRequest> taggedUsers;

    public CreatePostRequest(String caption, List<MediaRequest> media, List<TaggedUserRequest> taggedUsers) {
        this.caption = caption;
        this.media = media;
        this.taggedUsers = taggedUsers;
    }

    public static CreatePostRequest fromCaptionAndMedia(String caption, String mediaUrl, int width, int height, List<String> taggedUserIds) {
        List<MediaRequest> media = new ArrayList<>();
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            media.add(new MediaRequest(mediaUrl, width, height));
        }
        
        List<TaggedUserRequest> taggedUsers = new ArrayList<>();
        if (taggedUserIds != null) {
            for (String id : taggedUserIds) {
                taggedUsers.add(new TaggedUserRequest(id));
            }
        }
        
        return new CreatePostRequest(caption, media, taggedUsers);
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

    public static class TaggedUserRequest {
        @SerializedName("user_id")
        private final String userId;

        public TaggedUserRequest(String userId) {
            this.userId = userId;
        }
    }
}
