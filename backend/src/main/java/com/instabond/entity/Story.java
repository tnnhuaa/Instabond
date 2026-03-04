package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document(collection = "stories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Story {

    @Id
    private String id;

    private String author_id;

    private String media_url;

    private String type;

    private List<Viewer> viewers;

    private Instant expires_at;

    private Instant created_at;

    // Embedded Documents

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Viewer {
        private String user_id;
        private Instant viewed_at;
        private String reaction;
    }
}