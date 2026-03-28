package com.instabond.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "stories")
@CompoundIndexes({
        @CompoundIndex(name = "story_author_expiry_created_idx", def = "{'author_id': 1, 'expires_at': 1, 'created_at': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Story {

    @Id
    private String id;

    @Indexed
    private String author_id;

    private String media_url;

    private String type;

    private List<Viewer> viewers;

    @Indexed
    private Instant expires_at;

    @Indexed
    private Instant created_at;

    // Embedded Documents

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Viewer {
        private String user_id;
        private Instant viewed_at;
        private String reaction;
    }
}
