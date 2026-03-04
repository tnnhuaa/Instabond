package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    private List<String> participants;

    private LastMessage last_message;

    private String theme;

    private Instant updated_at;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LastMessage {
        private String content;
        private String sender_id;
        private Instant sent_at;
        private boolean is_read;
    }
}