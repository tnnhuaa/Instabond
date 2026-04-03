package com.instabond.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MusicSuggestionDTO {
    private String title;
    private String artist;
    private String previewUrl;
    private String coverUrl;
    private int durationSeconds;

    // Cloudinary URL
    private String uploadedImageUrl;
}