package com.instabond.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Message payload broadcast to clients listening on a conversation channel")
public class ChatMessageResponse {
    @Schema(description = "Unique ID of the saved message", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @JsonProperty("conversation_id")
    @Schema(description = "ID of the conversation this message belongs to", example = "64f1a2b3c4d5e6f7a8b9c0d2")
    private String conversationId;

    @JsonProperty("sender_id")
    @Schema(description = "ID of the user who sent the message", example = "64f1a2b3c4d5e6f7a8b9c0d3")
    private String senderId;

    @Schema(description = "Message type", allowableValues = {"text", "image"}, example = "text")
    private String type;
    @Schema(description = "Text content or media URL of the message", example = "Hello!")
    private String content;

    @JsonProperty("created_at")
    @Schema(description = "UTC timestamp of when the message was created")
    private Instant createdAt;
}
