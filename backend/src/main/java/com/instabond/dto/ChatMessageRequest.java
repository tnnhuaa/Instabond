package com.instabond.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload the client sends when posting a chat message via WebSocket")
public class ChatMessageRequest {

    @NotBlank(message = "conversation_id must not be blank")
    @JsonProperty("conversation_id")
    @Schema(description = "ID of the conversation this message belongs to", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String conversationId;

    @NotBlank(message = "content must not be blank")
    @Schema(description = "Text content of the message, or a media URL when type is 'image'", example = "Hello!")
    private String content;

    @Pattern(regexp = "text|image", message = "type must be 'text' or 'image'")
    @Schema(description = "Message type", allowableValues = {"text", "image"}, example = "text")
    private String type;
}
