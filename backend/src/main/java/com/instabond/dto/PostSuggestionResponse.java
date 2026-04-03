package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSuggestionResponse {
    private String image_url;
    private List<TaggedUserDTO> suggested_tags;
    // @TODO: Add music suggestion DTO
}