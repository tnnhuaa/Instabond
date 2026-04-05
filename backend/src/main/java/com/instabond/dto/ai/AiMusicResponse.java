package com.instabond.dto.ai;

import com.instabond.dto.MusicSuggestionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMusicResponse {
    private String scene_description;
    private List<MusicSuggestionDTO> suggestions;
}
