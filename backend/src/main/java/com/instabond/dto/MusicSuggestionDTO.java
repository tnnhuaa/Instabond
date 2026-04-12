package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Music suggestion generated from AI image analysis")
public class MusicSuggestionDTO {
    @Schema(example = "Golden Hour")
    private String song_name;

    @Schema(example = "JVKE")
    private String artist;

    @Schema(example = "https://cdn.example.com/preview.mp3", nullable = true)
    private String preview_url;

    @Schema(example = "Matches soft lighting and romantic portrait moments.")
    private String reason;
}
