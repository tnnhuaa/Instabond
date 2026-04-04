package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostSuggestionResponse {
    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("scene_description")
    private String sceneDescription;

    @SerializedName("music_suggestions")
    private List<MusicSuggestion> musicSuggestions;

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSceneDescription() {
        return sceneDescription;
    }

    public List<MusicSuggestion> getMusicSuggestions() {
        return musicSuggestions;
    }
}
