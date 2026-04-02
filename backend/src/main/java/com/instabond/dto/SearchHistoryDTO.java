package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Schema(
        name = "SearchHistoryDTO",
        description = "Search history item returned to client, optionally enriched with target user info for PROFILE type."
)
public class SearchHistoryDTO {

    @Schema(description = "Search history id", example = "67fb1f1b9d5a6f25a1c6d3c1")
    private String id;

    @Schema(description = "Search history type", example = "PROFILE", allowableValues = {"TEXT", "PROFILE"})
    private String type;

    @Schema(description = "Search keyword when type=TEXT", example = "penaldo")
    private String keyword;

    @Field("target_user_id")
    @Schema(description = "Target user id when type=PROFILE", example = "67fb1f1b9d5a6f25a1c6d3b0")
    private String targetUserId;

    @Field("target_username")
    @Schema(description = "Target username when type=PROFILE", example = "penaldo")
    private String targetUsername;

    @Field("target_full_name")
    @Schema(description = "Target full name when type=PROFILE", example = "Cristiano Penaldo")
    private String targetFullName;

    @Field("target_avatar_url")
    @Schema(description = "Target avatar URL when type=PROFILE", example = "https://cdn.instabond.com/avatar/u123.jpg")
    private String targetAvatarUrl;

    @Field("updated_at")
    @Schema(description = "Last updated time of this history record", example = "2026-04-02T09:15:30")
    private LocalDateTime updatedAt;
}