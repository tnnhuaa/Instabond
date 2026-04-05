package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class MusicSuggestion {
    @SerializedName("song_name")
    private String songName;

    @SerializedName("artist")
    private String artist;

    @SerializedName("preview_url")
    private String previewUrl;

    @SerializedName("reason")
    private String reason;

    private transient boolean aiRecommended;

    public MusicSuggestion() {
    }

    public MusicSuggestion(String songName, String artist, String previewUrl, String reason, boolean aiRecommended) {
        this.songName = songName;
        this.artist = artist;
        this.previewUrl = previewUrl;
        this.reason = reason;
        this.aiRecommended = aiRecommended;
    }

    public String getSongName() {
        return songName;
    }

    public String getArtist() {
        return artist;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAiRecommended() {
        return aiRecommended;
    }

    public void setAiRecommended(boolean aiRecommended) {
        this.aiRecommended = aiRecommended;
    }
}
