package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "relationships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Relationship {

    @Id
    private String id;

    private String requester_id;

    private String recipient_id;

    private String status;

    private String type;

    private int intimacy_score;

    private String friendship_level;

    private Streak streak;

    private InteractionHistory interaction_history;

    private Instant created_at;

    private Instant updated_at;

    // Embedded Documents

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Streak {
        private int count;
        private boolean has_fired_streak;
        private Instant last_interaction_date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionHistory {
        private int photos_together;
        private int stand_next_to_each_other;
    }
}