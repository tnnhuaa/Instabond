package com.example.instabond_fe.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.repository.ChatRepository;

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

    private String nextCursor;
    private boolean hasMore = true;
    private boolean isLoading;

    public InboxViewModel(@NonNull Application application) {
        super(application);
        repository = new ChatRepository(application);
    }

    public MutableLiveData<List<Conversation>> getInboxLiveData() {
        return inboxLiveData;
    }

    public void loadInbox() {
        if (isLoading) {
            return;
        }
        cachedConversations.clear();
        nextCursor = null;
        hasMore = true;
        loadPage(true);
    }

    public void loadNextPageIfNeeded() {
        if (isLoading || !hasMore) {
            return;
        }
        loadPage(false);
    }

    private void loadPage(boolean isFirstPage) {
        isLoading = true;
        String cursorToRequest = isFirstPage ? null : nextCursor;
        repository.fetchInbox(cursorToRequest, PAGE_LIMIT, new Callback<ConversationPageResponse>() {
            @Override
            public void onResponse(Call<ConversationPageResponse> call, Response<ConversationPageResponse> response) {
                isLoading = false;
                if (!response.isSuccessful() || response.body() == null) {
                    if (isFirstPage && cachedConversations.isEmpty()) {
                        inboxLiveData.setValue(new ArrayList<>());
                    }
                    return;
                }

                ConversationPageResponse body = response.body();
                List<Conversation> pageData = body.getData();
                if (isFirstPage) {
                    cachedConversations.clear();
                }
                if (pageData != null && !pageData.isEmpty()) {
                    cachedConversations.addAll(pageData);
                }

                nextCursor = body.getNextCursor();
                hasMore = body.isHasMore() && nextCursor != null && !nextCursor.trim().isEmpty();
                inboxLiveData.setValue(new ArrayList<>(cachedConversations));
            }

            @Override
            public void onFailure(Call<ConversationPageResponse> call, Throwable t) {
                isLoading = false;
                if (isFirstPage && cachedConversations.isEmpty()) {
                    inboxLiveData.setValue(new ArrayList<>());
                }
            }
        });
    }
}
