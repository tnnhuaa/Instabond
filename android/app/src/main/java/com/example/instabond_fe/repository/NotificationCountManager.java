package com.example.instabond_fe.repository;

import android.content.Context;
import android.util.Log;

import com.example.instabond_fe.model.UnreadCountResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationCountManager {
    private static final String TAG = "NotificationCountManager";
    private static NotificationCountManager instance;
    private final ApiService apiService;
    private long unreadCount = 0;
    private final List<UnreadCountListener> listeners = new ArrayList<>();

    public interface UnreadCountListener {
        void onUnreadCountUpdated(long count);
    }

    private NotificationCountManager(Context context) {
        this.apiService = ApiClient.getApiService(context);
    }

    public static synchronized NotificationCountManager getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationCountManager(context);
        }
        return instance;
    }

    public void fetchUnreadCount() {
        apiService.getUnreadNotificationCount().enqueue(new Callback<UnreadCountResponse>() {
            @Override
            public void onResponse(Call<UnreadCountResponse> call, Response<UnreadCountResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    unreadCount = response.body().getUnread_count();
                    notifyListeners();
                } else {
                    Log.e(TAG, "Failed to fetch unread count: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<UnreadCountResponse> call, Throwable t) {
                Log.e(TAG, "Error fetching unread count", t);
            }
        });
    }

    public void addListener(UnreadCountListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private void notifyListeners() {
        for (UnreadCountListener listener : listeners) {
            listener.onUnreadCountUpdated(unreadCount);
        }
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long count) {
        this.unreadCount = count;
        notifyListeners();
    }

    public void decrementUnreadCount() {
        if (unreadCount > 0) {
            unreadCount--;
            notifyListeners();
        }
    }
}

