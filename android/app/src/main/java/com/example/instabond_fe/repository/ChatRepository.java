package com.example.instabond_fe.repository;

import android.content.Context;

import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatRepository {
    private static volatile ChatRepository instance;

    private final ApiService apiService;
    private final WebSocketManager webSocketManager;
    private final Gson gson;

    private ChatRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.apiService = ApiClient.getApiService(appContext);
        this.webSocketManager = WebSocketManager.getInstance(appContext);
        this.gson = new Gson();
    }

    public static ChatRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ChatRepository.class) {
                if (instance == null) {
                    instance = new ChatRepository(context);
                }
            }
        }
        return instance;
    }

    public void connectRealtime() {
        webSocketManager.connectWebSocket();
    }

    public void disconnectRealtime() {
        webSocketManager.disconnect();
    }

    public void subscribeGlobalChannels() {
        webSocketManager.subscribeGlobalChannels();
    }

    public void subscribeConversation(String conversationId) {
        // Global queue delivers messages for every conversation, no per-room subscription needed.
    }

    public void unsubscribeConversation(String conversationId) {
        // No-op with global queue architecture.
    }

    public void sendRealtimeMessage(ChatMessageRequest request) {
        webSocketManager.sendMessage(request);
    }

    public void addRealtimeMessageListener(MessageListener listener) {
        webSocketManager.addConversationListener(listener);
    }

    public void removeRealtimeMessageListener(MessageListener listener) {
        webSocketManager.removeConversationListener(listener);
    }

    public void addInboxListener(WebSocketManager.InboxListener listener) {
        webSocketManager.addInboxListener(listener);
    }

    public void removeInboxListener(WebSocketManager.InboxListener listener) {
        webSocketManager.removeInboxListener(listener);
    }

    public void addConnectionListener(WebSocketManager.ConnectionListener listener) {
        webSocketManager.addConnectionListener(listener);
    }

    public void removeConnectionListener(WebSocketManager.ConnectionListener listener) {
        webSocketManager.removeConnectionListener(listener);
    }

    public void addOnlineStatusListener(WebSocketManager.OnlineStatusListener listener) {
        webSocketManager.addOnlineStatusListener(listener);
    }

    public void removeOnlineStatusListener(WebSocketManager.OnlineStatusListener listener) {
        webSocketManager.removeOnlineStatusListener(listener);
    }

    public void addNotificationListener(WebSocketManager.NotificationListener listener) {
        webSocketManager.addNotificationListener(listener);
    }

    public void removeNotificationListener(WebSocketManager.NotificationListener listener) {
        webSocketManager.removeNotificationListener(listener);
    }

    public void getOrCreateDirectConversation(String partnerId, ApiCallback<Conversation> callback) {
        enqueueCall(apiService.getOrCreateDirectConversation(partnerId), callback);
    }

    public void getHistory(String conversationId, int page, int size, ApiCallback<JsonElement> callback) {
        enqueueCall(apiService.getHistory(conversationId, page, size), callback);
    }

    public void getHistoryMessages(String conversationId, int page, int size, ApiCallback<List<ChatMessageResponse>> callback) {
        getHistory(conversationId, page, size, new ApiCallback<>() {
            @Override
            public void onSuccess(JsonElement data) {
                callback.onSuccess(parseHistoryPayload(data));
            }

            @Override
            public void onError(Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void uploadChatImage(String conversationId, MultipartBody.Part file, ApiCallback<ChatMessageResponse> callback) {
        enqueueCall(apiService.uploadChatImage(conversationId, file), callback);
    }

    public void markMessagesAsRead(String conversationId, ApiCallback<Void> callback) {
        enqueueCall(apiService.markMessagesAsRead(conversationId), callback);
    }

    public void fetchInbox(String cursor, int limit, Callback<ConversationPageResponse> callback) {
        apiService.getUserConversations(cursor, limit).enqueue(callback);
    }

    private List<ChatMessageResponse> parseHistoryPayload(JsonElement payload) {
        List<ChatMessageResponse> result = new ArrayList<>();
        if (payload == null || payload.isJsonNull()) {
            return result;
        }

        JsonArray source = null;
        if (payload.isJsonArray()) {
            source = payload.getAsJsonArray();
        } else if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonArray()) {
                source = object.getAsJsonArray("data");
            } else if (object.has("messages") && object.get("messages").isJsonArray()) {
                source = object.getAsJsonArray("messages");
            }
        }

        if (source == null) {
            return result;
        }

        Type listType = new TypeToken<List<ChatMessageResponse>>() {
        }.getType();
        List<ChatMessageResponse> parsed = gson.fromJson(source, listType);
        if (parsed != null) {
            result.addAll(parsed);
        }
        return result;
    }

    private <T> void enqueueCall(Call<T> call, ApiCallback<T> callback) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                    return;
                }
                callback.onError(new Exception("HTTP " + response.code()));
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                if (t instanceof IOException) {
                    callback.onError(t);
                } else {
                    callback.onError(new IOException(t));
                }
            }
        });
    }
}
