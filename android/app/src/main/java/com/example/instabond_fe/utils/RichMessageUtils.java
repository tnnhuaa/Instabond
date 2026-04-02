package com.example.instabond_fe.utils;

import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.StoryItem;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class RichMessageUtils {

    private static final Gson GSON = new Gson();

    private RichMessageUtils() {
    }

    public static String buildStoryReplyPayload(StoryItem story, String replyText) {
        StoryReplyPayload payload = new StoryReplyPayload();
        payload.storyId = story == null ? "" : story.getId();
        payload.authorId = story == null ? "" : story.getAuthorId();
        payload.authorUsername = story == null ? "" : story.getUsername();
        payload.mediaUrl = story == null ? "" : story.getMediaUrl();
        payload.createdAt = story == null ? "" : story.getCreatedAt();
        payload.replyText = replyText == null ? "" : replyText.trim();
        return GSON.toJson(payload);
    }

    public static StoryReplyPayload parseStoryReplyPayload(ChatMessageResponse message) {
        if (message == null || !"story_reply".equalsIgnoreCase(message.getType()) || isBlank(message.getContent())) {
            return null;
        }
        try {
            return GSON.fromJson(message.getContent(), StoryReplyPayload.class);
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }

    public static PostSharePayload parsePostSharePayload(ChatMessageResponse message) {
        if (message == null || !"post_share".equalsIgnoreCase(message.getType()) || isBlank(message.getContent())) {
            return null;
        }
        try {
            return GSON.fromJson(message.getContent(), PostSharePayload.class);
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }

    public static String getConversationPreview(ChatMessageResponse message) {
        if (message == null) {
            return "";
        }
        if (!isBlank(message.getPreviewText())) {
            return message.getPreviewText().trim();
        }

        String type = message.getType() == null ? "" : message.getType().trim().toLowerCase();
        if ("image".equals(type)) {
            return "sent a photo";
        }
        if ("post_share".equals(type)) {
            return "shared a post";
        }
        if ("story_reply".equals(type)) {
            return "replied to your story";
        }
        return isBlank(message.getContent()) ? "" : message.getContent().trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class StoryReplyPayload {
        private String storyId;
        private String authorId;
        private String authorUsername;
        private String mediaUrl;
        private String createdAt;
        private String replyText;

        public String getStoryId() {
            return storyId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public String getAuthorUsername() {
            return authorUsername;
        }

        public String getMediaUrl() {
            return mediaUrl;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getReplyText() {
            return replyText;
        }
    }

    public static class PostSharePayload {
        private String postId;
        private String username;
        private String imageUrl;
        private String caption;

        public String getPostId() {
            return postId;
        }

        public String getUsername() {
            return username;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getCaption() {
            return caption;
        }
    }
}
