package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Conversation item returned in inbox pagination")
public class ConversationDTO {

    @Schema(description = "Conversation id", example = "67dabc1234567890abcdef12")
    private String id;

    @Schema(description = "Conversation title; null for 1-on-1 chat, non-null for group chat", example = "6N4TURE Group")
    private String title;

    @Schema(description = "Participant users")
    private List<ParticipantDTO> participants;

    @Data
    public static class ParticipantDTO {
        private String id;
        private String username;
        private String avatar_url;
    }

    @Schema(description = "Latest message preview")
    private LastMessageDTO last_message;

    @Schema(description = "Conversation theme", example = "default")
    private String theme;

    @Schema(description = "Last updated time")
    private Instant updated_at;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Last message summary")
    public static class LastMessageDTO {
        @Schema(description = "Message content preview")
        private String content;

        @Schema(description = "Sender user id")
        private String sender_id;

        @Schema(description = "Message sent time")
        private Instant sent_at;

        @Schema(description = "Read flag")
        private boolean is_read;
    }
}
