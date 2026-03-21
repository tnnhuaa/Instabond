package com.example.instabond_fe.repository;

import com.example.instabond_fe.model.ChatMessageResponse;

public interface MessageListener {
    void onNewMessage(ChatMessageResponse message);
}

