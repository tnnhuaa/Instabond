package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Viewers summary for one story")
public class StoryViewersResponse {

    @Schema(description = "Story id", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String story_id;

    @Schema(description = "Total number of viewers", example = "12")
    private int viewer_count;

    @Schema(description = "Viewer list sorted by latest view first")
    private List<StoryViewerResponse> viewers;
}
