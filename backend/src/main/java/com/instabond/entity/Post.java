package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Document(collection = "posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    private String id;

    @Field(value = "author_id", targetType = FieldType.OBJECT_ID)
    private String author_id;

    private String caption;

    private Location location;

    private List<Media> media;

    private MusicSuggestion music_suggestion;

    private List<TaggedUser> tagged_users;

    private Stats stats;

    private Instant created_at;

    // Embedded Documents

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Location {
        private String name;
        private List<Double> coordinates;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Media {
        private String url;
        private int width;
        private int height;
        private List<String> ai_labels;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MusicSuggestion {
        private String song_name;
        private String artist;
        private String preview_url;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaggedUser {
        private String user_id;
        private String tag_type;
        private double confidence;
        private Position position;

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class Position {
            private double x;
            private double y;
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private int likes;
        private int comments;
        private int shares;
    }
}