package com.example.instabond_fe.model;

import com.google.gson.annotations.SerializedName;

public class UpdateAllowTaggingResponse {
    @SerializedName("allow_tagging")
    private String allowTagging;

    public String getAllowTagging() {
        return allowTagging;
    }
}
