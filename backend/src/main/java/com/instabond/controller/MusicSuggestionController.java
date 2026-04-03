package com.instabond.controller;

import com.instabond.dto.MusicSuggestionDTO;
import com.instabond.service.FileService;
import com.instabond.service.MusicSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/music-suggestions")
@RequiredArgsConstructor
@Tag(name = "AI Music", description = "AI Scene to Music APIs")
public class MusicSuggestionController {

    private final FileService fileService;
    private final MusicSuggestionService musicSuggestionService;

    @Operation(summary = "Upload image and get music suggestions based on AI scene analysis")
    @PostMapping(value = "/suggest-from-image", consumes = "multipart/form-data")
    public ResponseEntity<List<MusicSuggestionDTO>> suggestMusic(
            @RequestPart("image") MultipartFile image) {

        // Upload image to cloud storage and get URL
        String uploadedImageUrl = fileService.uploadImageUrl(image);

        // Get music suggestions based on the uploaded image
        List<MusicSuggestionDTO> suggestions = musicSuggestionService.suggestMusicFromImage(uploadedImageUrl);

        return ResponseEntity.ok(suggestions);
    }
}
