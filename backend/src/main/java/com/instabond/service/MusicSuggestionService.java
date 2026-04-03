package com.instabond.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabond.dto.MusicSuggestionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MusicSuggestionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MusicSuggestionDTO> suggestMusicFromImage(String uploadedImageUrl) {

        // TODO: Call Python AI Service to get vibe from image (list of keywords)
        // String aiVibe = restTemplate.getForObject("http://localhost:8000/analyze?url=" + uploadedImageUrl, String.class);
        String aiVibe = "rock"; // Hardcode

        try{
            // Deezer API
            String deezerUrl = "https://api.deezer.com/search?q=" + aiVibe + "&limit=5";
            String rawJsonResponse = restTemplate.getForObject(deezerUrl, String.class);

            JsonNode response = objectMapper.readTree(rawJsonResponse);

            List<MusicSuggestionDTO> suggestions = new ArrayList<>();
            if (response != null && response.has("data")) {
                for (JsonNode track : response.get("data")) {
                    suggestions.add(MusicSuggestionDTO.builder()
                            .title(track.path("title").asText())
                            .artist(track.path("artist").path("name").asText())
                            .previewUrl(track.path("preview").asText())
                            .coverUrl(track.path("artist").path("picture_medium").asText())
                            .durationSeconds(track.path("duration").asInt())
                            .uploadedImageUrl(uploadedImageUrl)
                            .build());
                }
            }
            return suggestions;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error from Deezer: " + e.getMessage());
        }
    }
}