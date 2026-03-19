package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private List<Double> face_embedding;

    private String qr_code_uid;

    private List<Badge> badges;

    private Setting settings;

    private Instant created_at;

    private Instant last_active;

    private java.util.Set<String> device_tokens;

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
        private String allow_tagging;
        private Boolean is_private;
        private String theme;
    }
}