package com.example.instabond_fe.repository;

import android.content.Context;

import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.google.gson.JsonElement;

import java.io.IOException;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatRepository {
    private final ApiService apiService;

    public ChatRepository(Context context) {
        this.apiService = ApiClient.getApiService(context);
    }

    public void getOrCreateDirectConversation(String partnerId, ApiCallback<Conversation> callback) {
        enqueueCall(apiService.getOrCreateDirectConversation(partnerId), callback);
    }

    public void getHistory(String conversationId, int page, int size, ApiCallback<JsonElement> callback) {
        enqueueCall(apiService.getHistory(conversationId, page, size), callback);
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
