package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSearchDTO {
    private String id;
    private String author_id;
    private String caption;
    private String thumbnail_url;   // Mapper: media[0].url
    private int likes;              // Mapper: stats.likes
    private int comments;           // Mapper: stats.comments
}
