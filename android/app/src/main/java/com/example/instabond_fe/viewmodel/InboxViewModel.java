package com.example.instabond_fe.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.model.LastMessage;
import com.example.instabond_fe.model.OnlineStatusEvent;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.repository.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InboxViewModel extends AndroidViewModel {
    private static final int PAGE_LIMIT = 20;

    private final MutableLiveData<List<Conversation>> inboxLiveData = new MutableLiveData<>(new ArrayList<>());
    private final ChatRepository repository;
    private final List<Conversation> cachedConversations = new ArrayList<>();
    private final Object inboxLock = new Object();
    private final WebSocketManager.InboxListener inboxListener = this::applyRealtimeMessage;
    private final WebSocketManager.OnlineStatusListener onlineStatusListener = this::applyOnlineStatus;

    private String nextCursor;
    private boolean hasMore = true;
    private boolean isLoading;

    public InboxViewModel(@NonNull Application application) {
        super(application);
        repository = ChatRepository.getInstance(application);

        repository.addInboxListener(inboxListener);
        // TODO: Re-enable or Refactor after deciding on Online Status UI logic
        repository.addOnlineStatusListener(onlineStatusListener);
        ensureRealtimeConnected();
    }

    public void loadInbox() {
        synchronized (inboxLock) {
            nextCursor = null;
            hasMore = true;
            isLoading = false;
            cachedConversations.clear();
            inboxLiveData.setValue(new ArrayList<>(cachedConversations));
        }
        loadNextPageIfNeeded();
    }

    public void loadNextPageIfNeeded() {
        final String requestCursor;
        synchronized (inboxLock) {
            if (isLoading || !hasMore) {
                return;
            }
            isLoading = true;
            requestCursor = nextCursor;
        }

        repository.fetchInbox(requestCursor, PAGE_LIMIT, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ConversationPageResponse> call, @NonNull Response<ConversationPageResponse> response) {
                List<Conversation> received = new ArrayList<>();
                String updatedCursor = null;
                boolean updatedHasMore = false;

                if (response.isSuccessful() && response.body() != null) {
                    ConversationPageResponse body = response.body();
                    if (body.getData() != null) {
                        received.addAll(body.getData());
                    }
                    updatedCursor = body.getNextCursor();
                    updatedHasMore = body.isHasMore();
                }

                synchronized (inboxLock) {
                    if (requestCursor == null) {
                        cachedConversations.clear();
                    }
                    mergeConversations(received);
                    nextCursor = updatedCursor;
                    hasMore = updatedHasMore;
                    isLoading = false;
                    inboxLiveData.postValue(new ArrayList<>(cachedConversations));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ConversationPageResponse> call, @NonNull Throwable t) {
                synchronized (inboxLock) {
                    isLoading = false;
                }
            }
        });
    }

    private void mergeConversations(List<Conversation> incoming) {
        for (Conversation item : incoming) {
            if (item == null || item.getId() == null) {
                continue;
            }
            int existingIndex = findConversationIndex(item.getId());
            if (existingIndex >= 0) {
                cachedConversations.set(existingIndex, item);
            } else {
                cachedConversations.add(item);
            }
        }
    }

    private void applyOnlineStatus(OnlineStatusEvent event) {
        if (event == null) {
            return;
        }

        synchronized (inboxLock) {
            boolean updated = false;
            for (Conversation conv : cachedConversations) {
                if (conv.getParticipants() == null) {
                    continue;
                }
                for (Conversation.Participant p : conv.getParticipants()) {
                    if (event.getEmail() != null && event.getEmail().equalsIgnoreCase(p.getEmail())) {
                        if (p.isOnline() != event.isOnline()) {
                            p.setOnline(event.isOnline());
                            updated = true;
                        }
                    }
                }
            }
            if (updated) {
                inboxLiveData.postValue(new ArrayList<>(cachedConversations));
            }
        }
    }

    private void applyRealtimeMessage(ChatMessageResponse message) {
        if (message == null || message.getConversationId() == null) {
            return;
        }

        boolean shouldReload = false;
        synchronized (inboxLock) {
            int index = findConversationIndex(message.getConversationId());
            if (index >= 0) {
                Conversation conversation = cachedConversations.remove(index);
                updateConversationPreview(conversation, message);
                cachedConversations.add(0, conversation);
                inboxLiveData.postValue(new ArrayList<>(cachedConversations));
            } else {
                shouldReload = true;
            }
        }

        // Re-sync inbox when realtime message belongs to a conversation not loaded yet.
        if (shouldReload) {
            loadInbox();
        }
    }

    private void updateConversationPreview(Conversation conversation, ChatMessageResponse message) {
        LastMessage last = conversation.getLastMessage();
        if (last == null) {
            last = new LastMessage();
            conversation.setLastMessage(last);
        }
        last.setContent(message.getContent());
        last.setSenderId(message.getSenderId());
        last.setSentAt(message.getCreatedAt());
        conversation.setUpdatedAt(message.getCreatedAt());
    }

    public void ensureRealtimeConnected() {
        repository.connectRealtime();
        repository.subscribeGlobalChannels();
    }

    public LiveData<List<Conversation>> getInboxLiveData() {
        return inboxLiveData;
    }

    private int findConversationIndex(String id) {
        for (int i = 0; i < cachedConversations.size(); i++) {
            if (id.equals(cachedConversations.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onCleared() {
        repository.removeInboxListener(inboxListener);
        repository.removeOnlineStatusListener(onlineStatusListener);
        super.onCleared();
    }
}
