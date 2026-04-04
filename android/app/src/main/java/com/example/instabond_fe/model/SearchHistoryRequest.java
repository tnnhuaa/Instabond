package com.example.instabond_fe.model;

public class SearchHistoryRequest {
    private String type;
    private String keyword;
    private String targetUserId;

    public SearchHistoryRequest(String type, String keyword, String targetUserId) {
        this.type = type;
        this.keyword = keyword;
        this.targetUserId = targetUserId;
    }
}