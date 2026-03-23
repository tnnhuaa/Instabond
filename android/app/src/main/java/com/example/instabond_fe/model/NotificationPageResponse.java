package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class NotificationPageResponse {
    private List<Notification> data;
    private int page;
    private int size;
    @SerializedName("total_elements")
    private int totalElements;
    @SerializedName("total_pages")
    private int totalPages;
    @SerializedName("has_next")
    private boolean hasNext;

    public List<Notification> getData() { return data; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isHasNext() { return hasNext; }
}
