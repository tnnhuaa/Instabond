package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StoryViewersResponse {
    @SerializedName(value = "story_id", alternate = {"storyId"})
    private String storyId;

    @SerializedName(value = "viewer_count", alternate = {"viewerCount"})
    private int viewerCount;

    @SerializedName("viewers")
    private List<StoryViewerResponse> viewers;

    public String getStoryId() {
        return storyId;
    }

    public int getViewerCount() {
        return viewerCount;
    }

    public List<StoryViewerResponse> getViewers() {
        return viewers;
    }
}
