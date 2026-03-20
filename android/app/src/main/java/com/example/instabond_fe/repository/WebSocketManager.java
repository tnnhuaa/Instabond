package com.example.instabond_fe.repository;

import android.content.Context;
import android.util.Log;

import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.SessionManager;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final String WS_SUFFIX = "ws";
    private static final String TOPIC_PREFIX = "/topic/conversations/";
    private static final String SEND_DESTINATION = "/app/chat.send";

    private final SessionManager sessionManager;
    private final Gson gson;
    private final CompositeDisposable compositeDisposable;

    private StompClient stompClient;

    public WebSocketManager(Context context) {
        this.sessionManager = new SessionManager(context.getApplicationContext());
        this.gson = new Gson();
        this.compositeDisposable = new CompositeDisposable();
    }

    public void connectWebSocket() {
        String wsUrl = buildWsUrl(ApiClient.getBaseUrl());
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

        List<StompHeader> headers = new ArrayList<>();
        String token = sessionManager.getAccessToken();
        if (token != null && !token.isEmpty()) {
            headers.add(new StompHeader("Authorization", "Bearer " + token));
        }

        Disposable lifecycleDisposable = stompClient.lifecycle().subscribe(lifecycleEvent -> {
            if (lifecycleEvent.getType() == LifecycleEvent.Type.OPENED) {
                Log.d(TAG, "CONNECTED");
            } else if (lifecycleEvent.getType() == LifecycleEvent.Type.ERROR) {
                Log.e(TAG, "ERROR", lifecycleEvent.getException());
            } else if (lifecycleEvent.getType() == LifecycleEvent.Type.CLOSED) {
                Log.d(TAG, "DISCONNECTED");
            }
        }, throwable -> Log.e(TAG, "ERROR", throwable));

        compositeDisposable.add(lifecycleDisposable);
        stompClient.connect(headers);
    }

    public void subscribeRoom(String conversationId, MessageListener listener) {
        if (stompClient == null) {
            return;
        }

        String topic = TOPIC_PREFIX + conversationId;
        Disposable topicDisposable = stompClient.topic(topic).subscribe(stompMessage -> {
            ChatMessageResponse message = gson.fromJson(stompMessage.getPayload(), ChatMessageResponse.class);
            listener.onNewMessage(message);
        }, throwable -> Log.e(TAG, "ERROR", throwable));

        compositeDisposable.add(topicDisposable);
    }

    public void sendMessage(ChatMessageRequest request) {
        if (stompClient == null) {
            return;
        }

        String payload = gson.toJson(request);
        Disposable sendDisposable = stompClient.send(SEND_DESTINATION, payload).subscribe();
        compositeDisposable.add(sendDisposable);
    }

    public void disconnect() {
        compositeDisposable.clear();
        if (stompClient != null) {
            stompClient.disconnect();
        }
    }

    private String buildWsUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.startsWith("https://")) {
            return "wss://" + normalized.substring("https://".length()) + "/" + WS_SUFFIX;
        }
        if (normalized.startsWith("http://")) {
            return "ws://" + normalized.substring("http://".length()) + "/" + WS_SUFFIX;
        }
        return normalized + "/" + WS_SUFFIX;
    }
}

