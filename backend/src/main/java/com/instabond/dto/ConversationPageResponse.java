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
@Schema(description = "Cursor pagination response for conversation inbox")
public class ConversationPageResponse {

    @Schema(description = "Conversation items sorted by updated_at descending")
    private List<ConversationDTO> data;

    @Schema(description = "Cursor to request the next page; null means no more data")
    private Instant next_cursor;

    @Schema(description = "Whether there are more records after this page")
    private boolean has_more;

    @Schema(description = "Effective limit used by server")
    private int limit;
}
