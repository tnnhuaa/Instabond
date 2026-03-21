package com.example.instabond_fe.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.OnlineStatusEvent;
import com.example.instabond_fe.repository.ApiCallback;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.repository.MessageListener;
import com.example.instabond_fe.repository.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends AndroidViewModel {
    private final ChatRepository chatRepository;
    private final MutableLiveData<List<ChatMessageResponse>> messagesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> partnerOnlineLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> connectionLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>("");
    private final String currentUserId;

    private final MessageListener messageListener = this::handleRealtimeMessage;
    private final WebSocketManager.OnlineStatusListener onlineStatusListener = this::handleOnlineStatus;
    private final WebSocketManager.ConnectionListener connectionListener = new WebSocketManager.ConnectionListener() {
        @Override
        public void onConnected() {
            connectionLiveData.postValue(true);
        }

        @Override
        public void onDisconnected() {
            connectionLiveData.postValue(false);
        }

        @Override
        public void onError(Throwable throwable) {
            connectionLiveData.postValue(false);
            errorLiveData.postValue(throwable == null ? "Socket error" : throwable.getMessage());
        }
    };

    private String activeConversationId;
    private String trackedPartnerEmail;

    private final java.util.Set<String> seenMessageKeys = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final int HISTORY_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_SCAN_PAGES = 20;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        chatRepository = ChatRepository.getInstance(application);
        currentUserId = new com.example.instabond_fe.network.SessionManager(application).getUserId();

        chatRepository.addRealtimeMessageListener(messageListener);
        chatRepository.addOnlineStatusListener(onlineStatusListener);
        chatRepository.addConnectionListener(connectionListener);
    }

    private void handleRealtimeMessage(ChatMessageResponse message) {
        if (message == null || activeConversationId == null || !activeConversationId.equals(message.getConversationId())) {
            return;
        }

        String key = toMessageKey(message);
        if (seenMessageKeys.contains(key)) {
            return;
        }
        seenMessageKeys.add(key);

        List<ChatMessageResponse> current = messagesLiveData.getValue();
        List<ChatMessageResponse> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
        updated.add(message);
        messagesLiveData.postValue(updated);
    }

    private void handleOnlineStatus(OnlineStatusEvent event) {
        if (event == null || trackedPartnerEmail == null) {
            return;
        }
        // TODO: Refactor Online Status
        if (event.getEmail() != null && event.getEmail().equalsIgnoreCase(trackedPartnerEmail)) {
            partnerOnlineLiveData.postValue(event.isOnline());
        }
    }

    public void startChat(String conversationId, String partnerId, String partnerEmail, boolean initialOnline) {
        this.activeConversationId = conversationId;
        this.trackedPartnerEmail = partnerEmail;
        partnerOnlineLiveData.setValue(initialOnline);

        seenMessageKeys.clear();
        messagesLiveData.setValue(new ArrayList<>());

        ensureRealtimeConnected();
        loadLatestHistoryWindow(conversationId);
    }

    public void ensureRealtimeConnected() {
        chatRepository.connectRealtime();
        chatRepository.subscribeGlobalChannels();
    }

    public void sendTextMessage(String text) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.isEmpty()) {
            return;
        }
        if (activeConversationId == null || activeConversationId.trim().isEmpty()) {
            errorLiveData.setValue("Missing conversation");
            return;
        }

        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId(activeConversationId);
        request.setContent(safeText);
        request.setType("TEXT");
        chatRepository.sendRealtimeMessage(request);
    }

    public void stopChat() {
        activeConversationId = null;
        trackedPartnerEmail = null;
        seenMessageKeys.clear();
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public LiveData<List<ChatMessageResponse>> getMessagesLiveData() {
        return messagesLiveData;
    }

    public LiveData<Boolean> getPartnerOnlineLiveData() {
        return partnerOnlineLiveData;
    }

    public LiveData<Boolean> getConnectionLiveData() {
        return connectionLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    private void loadHistory(String conversationId) {
        loadLatestHistoryWindow(conversationId);
    }

    private void loadLatestHistoryWindow(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }
        scanHistoryForLatestPage(conversationId, 0, new ArrayList<>(), "");
    }

    private void scanHistoryForLatestPage(
            String conversationId,
            int page,
            List<ChatMessageResponse> bestPage,
            String bestMaxCreatedAt
    ) {
        if (page >= MAX_HISTORY_SCAN_PAGES) {
            mergeAndPublishMessages(bestPage);
            return;
        }

        chatRepository.getHistoryMessages(conversationId, page, HISTORY_PAGE_SIZE, new ApiCallback<>() {
            @Override
            public void onSuccess(List<ChatMessageResponse> data) {
                List<ChatMessageResponse> currentPage = new ArrayList<>(data == null ? new ArrayList<>() : data);
                if (currentPage.isEmpty()) {
                    mergeAndPublishMessages(bestPage);
                    return;
                }

                String currentMaxCreatedAt = findMaxCreatedAt(currentPage);
                List<ChatMessageResponse> nextBestPage = bestPage;
                String nextBestMaxCreatedAt = bestMaxCreatedAt;

                if (nextBestPage.isEmpty() || compareCreatedAt(currentMaxCreatedAt, nextBestMaxCreatedAt) > 0) {
                    nextBestPage = currentPage;
                    nextBestMaxCreatedAt = currentMaxCreatedAt;
                }

                if (currentPage.size() < HISTORY_PAGE_SIZE) {
                    mergeAndPublishMessages(nextBestPage);
                    return;
                }

                scanHistoryForLatestPage(conversationId, page + 1, nextBestPage, nextBestMaxCreatedAt);
            }

            @Override
            public void onError(Throwable t) {
                if (!bestPage.isEmpty()) {
                    mergeAndPublishMessages(bestPage);
                } else {
                    errorLiveData.postValue(t == null ? "Failed to load chat history" : t.getMessage());
                }
            }
        });
    }

    private void mergeAndPublishMessages(List<ChatMessageResponse> incoming) {
        List<ChatMessageResponse> current = messagesLiveData.getValue();
        java.util.LinkedHashMap<String, ChatMessageResponse> merged = new java.util.LinkedHashMap<>();

        if (current != null) {
            for (ChatMessageResponse item : current) {
                if (item != null) {
                    merged.put(toMessageKey(item), item);
                }
            }
        }
        if (incoming != null) {
            for (ChatMessageResponse item : incoming) {
                if (item != null) {
                    merged.put(toMessageKey(item), item);
                }
            }
        }

        List<ChatMessageResponse> ordered = new ArrayList<>(merged.values());
        ordered.sort((left, right) -> compareCreatedAt(
                left == null ? null : left.getCreatedAt(),
                right == null ? null : right.getCreatedAt()
        ));

        seenMessageKeys.clear();
        for (ChatMessageResponse item : ordered) {
            seenMessageKeys.add(toMessageKey(item));
        }
        messagesLiveData.postValue(ordered);
    }

    private String findMaxCreatedAt(List<ChatMessageResponse> page) {
        String max = "";
        for (ChatMessageResponse item : page) {
            String createdAt = item == null || item.getCreatedAt() == null ? "" : item.getCreatedAt();
            if (compareCreatedAt(createdAt, max) > 0) {
                max = createdAt;
            }
        }
        return max;
    }

    private int compareCreatedAt(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        return safeLeft.compareTo(safeRight);
    }

    private String toMessageKey(ChatMessageResponse message) {
        if (message == null) {
            return "null";
        }
        if (message.getId() != null && !message.getId().trim().isEmpty()) {
            return message.getId();
        }
        String conv = message.getConversationId() == null ? "" : message.getConversationId();
        String sender = message.getSenderId() == null ? "" : message.getSenderId();
        String createdAt = message.getCreatedAt() == null ? "" : message.getCreatedAt();
        String content = message.getContent() == null ? "" : message.getContent();
        return conv + "|" + sender + "|" + createdAt + "|" + content;
    }

    @Override
    protected void onCleared() {
        chatRepository.removeRealtimeMessageListener(messageListener);
        chatRepository.removeOnlineStatusListener(onlineStatusListener);
        chatRepository.removeConnectionListener(connectionListener);
        super.onCleared();
    }
}
