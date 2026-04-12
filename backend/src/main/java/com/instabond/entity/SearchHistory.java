package com.instabond.entity;

import com.instabond.enums.SearchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "search_histories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {
    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    private SearchType type;        // "TEXT" | "PROFILE"

    private String keyword;         // type = TEXT

    @Field("target_user_id")
    private String targetUserId;    // type = PROFILE

    @Field("updated_at")
    private LocalDateTime updatedAt;
}