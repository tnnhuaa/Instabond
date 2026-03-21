package com.example.instabond_fe.repository;

import android.content.Context;
import android.util.Log;

import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.OnlineStatusEvent;
import com.example.instabond_fe.model.WsEvent;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;

public class WebSocketManager {
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(Throwable throwable);
    }

    public interface OnlineStatusListener {
        void onStatusChanged(OnlineStatusEvent event);
    }

    public interface NotificationListener {
        void onNotification(String payload);
    }

    public interface InboxListener {
        void onConversationUpdated(ChatMessageResponse lastMessage);
    }

    private static final String TAG = "WebSocketManager";
    private static final String WS_SUFFIX = "ws";
    private static final String SEND_DESTINATION = "/app/chat.send";
    private static final String EVENTS_QUEUE = "/user/queue/events";

    private static final int HEARTBEAT_MS = 10_000;
    private static final long RECONNECT_BASE_DELAY_MS = 1_000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000L;

    private static volatile WebSocketManager instance;

    private final SessionManager sessionManager;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final Set<OnlineStatusListener> onlineStatusListeners = new CopyOnWriteArraySet<>();
    private final Set<NotificationListener> notificationListeners = new CopyOnWriteArraySet<>();
    private final Set<InboxListener> inboxListeners = new CopyOnWriteArraySet<>();

    private final Set<Disposable> sendDisposables = new CopyOnWriteArraySet<>();
    private final Queue<ChatMessageRequest> pendingOutgoingMessages = new ConcurrentLinkedQueue<>();

    private StompClient stompClient;
    private Disposable lifecycleDisposable;
    private Disposable eventsDisposable;
    private ScheduledFuture<?> reconnectFuture;

    private boolean manualDisconnect;
    private int reconnectAttempt;
    private volatile boolean isConnecting;
    private volatile boolean isSocketOpened;

    private WebSocketManager(Context context) {
        this.sessionManager = new SessionManager(context.getApplicationContext());
        this.gson = new Gson();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public static WebSocketManager getInstance(Context context) {
        if (instance == null) {
            synchronized (WebSocketManager.class) {
                if (instance == null) {
                    instance = new WebSocketManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public synchronized void connectWebSocket() {
        manualDisconnect = false;
        if (stompClient != null && stompClient.isConnected()) {
            isSocketOpened = true;
            isConnecting = false;
            subscribeGlobalChannelsInternal();
            notifyConnected();
            return;
        }
        if (isSocketOpened || isConnecting) return;
        connectInternal();
    }

    public synchronized void disconnect() {
        manualDisconnect = true;
        boolean wasOpened = isSocketOpened;
        isSocketOpened = false;
        cancelReconnect();
        disposeAllSubscriptions();
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
        if (wasOpened) {
            notifyDisconnected();
        }
    }

    public synchronized void subscribeGlobalChannels() {
        if (stompClient == null || !isSocketOpened) return;
        subscribeGlobalChannelsInternal();
    }

    private void subscribeGlobalChannelsInternal() {
        subscribeEventsInternal();
    }

    private void subscribeEventsInternal() {
        if (stompClient == null) return;
        if (eventsDisposable != null && !eventsDisposable.isDisposed()) return;

        eventsDisposable = stompClient.topic(EVENTS_QUEUE).subscribe(stompMessage -> {
            WsEvent event = gson.fromJson(stompMessage.getPayload(), WsEvent.class);
            if (event == null || event.getType() == null) return;

            JsonElement payload = event.getPayload();
            String type = event.getType();

            if (WsEvent.TYPE_CHAT.equalsIgnoreCase(type)) {
                ChatMessageResponse message = gson.fromJson(payload, ChatMessageResponse.class);
                if (message == null) return;

                // 1. Notify active Chat screen
                if (!messageListeners.isEmpty()) {
                    for (MessageListener listener : messageListeners) {
                        listener.onNewMessage(message);
                    }
                }

                // 2. Always notify Inbox screen to move conversation to top and update preview
                if (!inboxListeners.isEmpty()) {
                    for (InboxListener listener : inboxListeners) {
                        listener.onConversationUpdated(message);
                    }
                }

                // TODO: If both listener sets are empty, the app is likely in another screen.
                // The Backend is expected to send a Push Notification (FCM) in this case.
                return;
            }

            if (WsEvent.TYPE_PRESENCE.equalsIgnoreCase(type)) {
                OnlineStatusEvent status = gson.fromJson(payload, OnlineStatusEvent.class);
                if (status == null) return;

                // TODO: Re-enable UI status update after refining the Online/Offline logic
                for (OnlineStatusListener listener : onlineStatusListeners) {
                    listener.onStatusChanged(status);
                }
                return;
            }

            if (WsEvent.TYPE_NOTIFICATION.equalsIgnoreCase(type)) {
                String rawPayload = payload == null ? "{}" : payload.toString();
                // TODO: Handle internal system notifications sent via Socket here
                for (NotificationListener listener : notificationListeners) {
                    listener.onNotification(rawPayload);
                }
                return;
            }

            if (WsEvent.TYPE_ERROR.equalsIgnoreCase(type)) {
                notifyError(new RuntimeException(payload == null ? "Socket error" : payload.toString()));
            }
        }, throwable -> {
            Log.e(TAG, "Events queue error", throwable);
            notifyError(throwable);
            scheduleReconnect();
        });
    }

    public synchronized void sendMessage(ChatMessageRequest request) {
        if (request == null) return;
        if (stompClient == null || isConnecting || !isSocketOpened || !stompClient.isConnected()) {
            pendingOutgoingMessages.offer(cloneRequest(request));
            connectWebSocket();
            return;
        }
        String payload = gson.toJson(request);
        Disposable sendDisposable = stompClient.send(SEND_DESTINATION, payload).subscribe(() -> {}, throwable -> {
            pendingOutgoingMessages.offer(cloneRequest(request));
            notifyError(throwable);
            scheduleReconnect();
        });
        sendDisposables.add(sendDisposable);
    }

    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
            if (isSocketOpened) {
                listener.onConnected();
            }
        }
    }

    public void removeConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            connectionListeners.remove(listener);
        }
    }

    public void addConversationListener(MessageListener listener) { if (listener != null) messageListeners.add(listener); }
    public void removeConversationListener(MessageListener listener) { if (listener != null) messageListeners.remove(listener); }

    public void addOnlineStatusListener(OnlineStatusListener listener) { if (listener != null) onlineStatusListeners.add(listener); }
    public void removeOnlineStatusListener(OnlineStatusListener listener) { if (listener != null) onlineStatusListeners.remove(listener); }

    public void addNotificationListener(NotificationListener listener) {
        if (listener != null) {
            notificationListeners.add(listener);
        }
    }

    public void removeNotificationListener(NotificationListener listener) {
        if (listener != null) {
            notificationListeners.remove(listener);
        }
    }

    public void addInboxListener(InboxListener listener) { if (listener != null) inboxListeners.add(listener); }
    public void removeInboxListener(InboxListener listener) { if (listener != null) inboxListeners.remove(listener); }

    private void connectInternal() {
        if (isConnecting) return;
        isConnecting = true;
        if (stompClient != null) {
            try { stompClient.disconnect(); } catch (Exception ignored) {}
            stompClient = null;
        }
        disposeAllSubscriptions();
        String wsUrl = buildWsUrl(ApiClient.getBaseUrl());
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);
        stompClient.withClientHeartbeat(HEARTBEAT_MS).withServerHeartbeat(HEARTBEAT_MS);
        List<StompHeader> headers = new ArrayList<>();
        String token = sessionManager.getAccessToken();
        if (token != null && !token.isEmpty()) headers.add(new StompHeader("Authorization", "Bearer " + token));
        lifecycleDisposable = stompClient.lifecycle().subscribe(this::handleLifecycleEvent, throwable -> {
            isConnecting = false;
            notifyError(throwable);
            scheduleReconnect();
        });
        stompClient.connect(headers);
    }

    private void handleLifecycleEvent(LifecycleEvent lifecycleEvent) {
        if (lifecycleEvent.getType() == LifecycleEvent.Type.OPENED) {
            isConnecting = false; isSocketOpened = true; reconnectAttempt = 0;
            cancelReconnect(); subscribeGlobalChannelsInternal();
            notifyConnected(); flushPendingMessages();
        } else if (lifecycleEvent.getType() == LifecycleEvent.Type.ERROR) {
            isConnecting = false;
            isSocketOpened = false;
            notifyError(lifecycleEvent.getException());
            scheduleReconnect();
        } else if (lifecycleEvent.getType() == LifecycleEvent.Type.CLOSED) {
            isConnecting = false;
            boolean wasOpened = isSocketOpened;
            isSocketOpened = false;
            if (wasOpened) {
                notifyDisconnected();
            }
            scheduleReconnect();
        }
    }

    private synchronized void disposeAllSubscriptions() {
        if (lifecycleDisposable != null && !lifecycleDisposable.isDisposed()) {
            lifecycleDisposable.dispose();
        }
        lifecycleDisposable = null;

        if (eventsDisposable != null && !eventsDisposable.isDisposed()) {
            eventsDisposable.dispose();
        }
        eventsDisposable = null;

        for (Disposable d : sendDisposables) {
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        }
        sendDisposables.clear();
    }

    private synchronized void scheduleReconnect() {
        if (manualDisconnect || (reconnectFuture != null && !reconnectFuture.isDone())) return;
        long delay = Math.min(RECONNECT_BASE_DELAY_MS * (1L << Math.min(reconnectAttempt, 5)), RECONNECT_MAX_DELAY_MS);
        reconnectAttempt++;
        reconnectFuture = scheduler.schedule(this::connectInternal, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelReconnect() { if (reconnectFuture != null) { reconnectFuture.cancel(true); reconnectFuture = null; } }
    private void flushPendingMessages() {
        if (stompClient == null || isConnecting) return;
        ChatMessageRequest queued;
        while ((queued = pendingOutgoingMessages.poll()) != null) sendMessage(queued);
    }
    private ChatMessageRequest cloneRequest(ChatMessageRequest original) { return new ChatMessageRequest(original.getConversationId(), original.getContent(), original.getType()); }
    private void notifyConnected() { for (ConnectionListener l : connectionListeners) l.onConnected(); }
    private void notifyDisconnected() {
        for (ConnectionListener listener : connectionListeners) {
            listener.onDisconnected();
        }
    }

    private void notifyError(Throwable throwable) {
        for (ConnectionListener listener : connectionListeners) {
            listener.onError(throwable);
        }
    }

    private String buildWsUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return (normalized.startsWith("https") ? "wss" : "ws") + "://" + normalized.substring(normalized.indexOf("//") + 2) + "/" + WS_SUFFIX;
    }
}
