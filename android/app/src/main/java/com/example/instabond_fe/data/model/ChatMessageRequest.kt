package com.example.instabond_fe.data.model

import com.google.gson.annotations.SerializedName

data class ChatMessageRequest(
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("type")
    val type: String?
)

