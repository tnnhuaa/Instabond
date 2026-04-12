package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String username;

    private String email;

    private String password;

    private String full_name;

    private String phone_number;

    private String avatar_url;

    private String bio;

    private String qr_code_uid;

    private List<Badge> badges;

    private Setting settings;

    private Instant created_at;

    private Instant last_active;

    private java.util.Set<String> device_tokens;

    @Field("registration_image_urls")
    private List<String> registrationImageUrls;

    @Field("face_embedding")
    private List<Double> faceEmbedding;

    // Embedded Documents

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Badge {
        private String type;
        private String name;
        private Instant earned_at;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Setting {
        @Builder.Default
        private String allow_tagging = "everyone";

        @Builder.Default
        private Boolean is_private = false;

        private String theme;
    }
}