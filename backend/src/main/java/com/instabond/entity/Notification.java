package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    private String recipient_id;

    private String sender_id;

    private String type;

    private String content;

    private boolean is_read;

    private Metadata metadata;

    private Instant created_at;

    // Embedded Documents

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Metadata {
        private String new_level;
        private String relationship_id;
        private String post_id;
    }
}