package com.example.instabond_fe;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.MessagePopupHelper;
import com.example.instabond_fe.utils.ThemePreferenceManager;
import com.example.instabond_fe.view.ChatActivity;
import com.example.instabond_fe.view.SignInActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InstabondApplication extends Application {

    private Activity currentActivity;
    private SessionManager sessionManager;
    private ApiService apiService;
    private volatile boolean sessionExpiredPending;
    private volatile boolean sessionExpiredDialogShowing;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferenceManager.applySavedTheme(this);
        sessionManager = new SessionManager(this);
        apiService = ApiClient.getApiService(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                // Keep WebSocket alive as long as any Activity is in the foreground
                if (sessionManager.isLoggedIn() && !(activity instanceof SignInActivity)) {
                    ChatRepository.getInstance(InstabondApplication.this).connectRealtime();
                    ChatRepository.getInstance(InstabondApplication.this).subscribeGlobalChannels();
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivity = activity;
                if (sessionExpiredPending) {
                    showSessionExpiredDialog(activity);
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });

        // Load cached usernames for conversations
        if (sessionManager.isLoggedIn()) {
            preCacheUserNames();
        }

        // Listen for incoming messages via WebSocket
        WebSocketManager.getInstance(this).addInboxListener(lastMessage -> {
            if (currentActivity == null || lastMessage == null) return;

            currentActivity.runOnUiThread(() -> {
                if (lastMessage.getSenderId().equals(sessionManager.getUserId())) return;

                if (currentActivity instanceof ChatActivity) {
                    return;
                }

                String cachedName = WebSocketManager.getInstance(this).getCachedUsername(lastMessage.getSenderId());
                
                // Show popup
                MessagePopupHelper.showInAppNotification(currentActivity, lastMessage, cachedName);
            });
        });
    }

    private void preCacheUserNames() {
        apiService.getUserConversations(null, 50).enqueue(new Callback<ConversationPageResponse>() {
            @Override
            public void onResponse(@NonNull Call<ConversationPageResponse> call, @NonNull Response<ConversationPageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ConversationPageResponse page = response.body();
                    if (page.getData() != null) {
                        for (Conversation conv : page.getData()) {
                            if (conv.getParticipants() != null) {
                                for (Conversation.Participant p : conv.getParticipants()) {
                                    WebSocketManager.getInstance(InstabondApplication.this).cacheUser(p.getId(), p.getUsername());
                                }
                            }
                        }
                    }
                }
            }

            @Override public void onFailure(@NonNull Call<ConversationPageResponse> call, @NonNull Throwable t) {}
        });
    }

    public void notifySessionExpired() {
        sessionExpiredPending = true;
        Activity activity = currentActivity;
        if (activity != null) {
            showSessionExpiredDialog(activity);
        }
    }

    private void showSessionExpiredDialog(@NonNull Activity activity) {
        if (sessionExpiredDialogShowing) {
            return;
        }
        sessionExpiredDialogShowing = true;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                sessionExpiredDialogShowing = false;
                return;
            }

            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.session_expired_title)
                    .setMessage(R.string.msg_login_expired)
                    .setCancelable(false)
                    .setPositiveButton(R.string.session_expired_action_ok, (dialog, which) -> {
                        dialog.dismiss();
                        forceLogout(activity);
                    })
                    .setNegativeButton(R.string.session_expired_action_close, (dialog, which) -> {
                        dialog.dismiss();
                        forceLogout(activity);
                    })
                    .setOnDismissListener(dialog -> {
                        sessionExpiredDialogShowing = false;
                        sessionExpiredPending = false;
                    })
                    .show();
        });
    }

    private void forceLogout(@NonNull Activity activity) {
        ChatRepository.getInstance(activity.getApplicationContext()).disconnectRealtime();
        sessionManager.clearSession();
        Intent intent = new Intent(activity, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
