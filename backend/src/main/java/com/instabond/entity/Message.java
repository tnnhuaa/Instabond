package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    private String conversation_id;

    private String sender_id;

    private String type;

    private String content;

    private boolean is_view_once;

    private boolean is_viewed;

    private List<Reaction> reactions;

    private List<ReadReceipt> read_by;

    private Instant created_at;

    // Embedded Documents

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Reaction {
        private String user_id;
        private String icon;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReadReceipt {
        private String user_id;
        private Instant read_at;
    }
}