package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ConversationPageResponse {
    @SerializedName("data")
    private List<Conversation> data;

    @SerializedName("next_cursor")
    private String nextCursor;

    @SerializedName("has_more")
    private boolean hasMore;

    @SerializedName("limit")
    private int limit;

    public List<Conversation> getData() {
        return data;
    }

    public void setData(List<Conversation> data) {
        this.data = data;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}

