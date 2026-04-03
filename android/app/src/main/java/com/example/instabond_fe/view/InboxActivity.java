package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.databinding.ActivityInboxBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.viewmodel.InboxViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InboxActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private static final int LOAD_MORE_THRESHOLD = 4;

    private ActivityInboxBinding binding;
    private InboxAdapter adapter;
    private InboxActiveAdapter activeAdapter;
    private InboxViewModel viewModel;
    private ApiService apiService;
    private String currentUserId;
    private final List<Conversation> allConversations = new ArrayList<>();
    private final Set<String> requestedProfileIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SessionManager sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();
        apiService = ApiClient.getApiService(this);

        adapter = new InboxAdapter(this, this::openConversation);
        activeAdapter = new InboxActiveAdapter(this, this::openConversation);

        setupLists();
        bindActions();

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        viewModel.getInboxLiveData().observe(this, conversations -> {
            allConversations.clear();
            if (conversations != null) {
                allConversations.addAll(conversations);
            }
            hydrateMissingParticipantProfiles(allConversations);
            applyFilters();
        });

        viewModel.loadInbox();
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.ensureRealtimeConnected();
    }

    private void setupLists() {
        LinearLayoutManager inboxLayoutManager = new LinearLayoutManager(this);
        binding.rvInbox.setLayoutManager(inboxLayoutManager);
        binding.rvInbox.setAdapter(adapter);
        binding.rvInbox.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) {
                    return;
                }
                int lastVisible = inboxLayoutManager.findLastVisibleItemPosition();
                int totalCount = adapter.getItemCount();
                if (lastVisible >= totalCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadNextPageIfNeeded();
                }
            }
        });

        binding.rvActiveUsers.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvActiveUsers.setAdapter(activeAdapter);
    }

    private void bindActions() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void applyFilters() {
        String query = binding.etSearch.getText() == null
                ? ""
                : binding.etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());

        List<Conversation> filtered = new ArrayList<>();
        for (Conversation conversation : allConversations) {
            if (matchesQuery(conversation, query)) {
                filtered.add(conversation);
            }
        }

        adapter.setConversations(filtered);
        adapter.notifyDataSetChanged();

        List<Conversation> activeItems = buildActiveNowList(filtered.isEmpty() && query.isEmpty() ? allConversations : filtered);
        activeAdapter.setConversations(activeItems);
        activeAdapter.notifyDataSetChanged();
    }

    private boolean matchesQuery(Conversation conversation, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (conversation == null) {
            return false;
        }

        String title = conversation.getTitle() == null ? "" : conversation.getTitle().toLowerCase(Locale.getDefault());
        if (title.contains(query)) {
            return true;
        }

        Conversation.Participant peer = findPeer(conversation, currentUserId);
        if (peer != null && peer.getUsername() != null
                && peer.getUsername().toLowerCase(Locale.getDefault()).contains(query)) {
            return true;
        }

        if (conversation.getLastMessage() != null && conversation.getLastMessage().getContent() != null) {
            return conversation.getLastMessage().getContent()
                    .toLowerCase(Locale.getDefault())
                    .contains(query);
        }
        return false;
    }

    private List<Conversation> buildActiveNowList(List<Conversation> source) {
        List<Conversation> onlineFirst = new ArrayList<>();
        List<Conversation> fallback = new ArrayList<>();
        java.util.LinkedHashSet<String> seenPeerIds = new java.util.LinkedHashSet<>();

        for (Conversation conversation : source) {
            Conversation.Participant peer = findPeer(conversation, currentUserId);
            if (peer == null || peer.getId() == null || !seenPeerIds.add(peer.getId())) {
                continue;
            }
            if (peer.isOnline()) {
                onlineFirst.add(conversation);
            } else {
                fallback.add(conversation);
            }
        }

        if (!onlineFirst.isEmpty()) {
            return onlineFirst.size() > 6 ? onlineFirst.subList(0, 6) : onlineFirst;
        }
        return fallback.size() > 6 ? fallback.subList(0, 6) : fallback;
    }

    private void openConversation(Conversation conversation) {
        Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
        intent.putExtra("CONVERSATION_ID", conversation.getId());
        intent.putExtra("conversationId", conversation.getId());

        Conversation.Participant peer = findPeer(conversation, currentUserId);
        if (peer != null) {
            intent.putExtra("PARTNER_ID", peer.getId());
            intent.putExtra("PARTNER_NAME", peer.getUsername());
            intent.putExtra("PARTNER_EMAIL", peer.getEmail());
            intent.putExtra("PARTNER_AVATAR", peer.getAvatarUrl());
            intent.putExtra("PARTNER_ONLINE", peer.isOnline());

            intent.putExtra("partnerId", peer.getId());
            intent.putExtra("partnerName", peer.getUsername());
            intent.putExtra("partnerEmail", peer.getEmail());
            intent.putExtra("partnerAvatar", peer.getAvatarUrl());
            intent.putExtra("partnerOnline", peer.isOnline());
        }

        startActivity(intent);
    }

    private Conversation.Participant findPeer(Conversation conversation, String safeCurrentUserId) {
        if (conversation == null || conversation.getParticipants() == null) {
            return null;
        }

        String current = safeCurrentUserId == null ? "" : safeCurrentUserId;
        for (Conversation.Participant participant : conversation.getParticipants()) {
            if (participant.getId() == null || !participant.getId().equals(current)) {
                return participant;
            }
        }
        return null;
    }

    private void hydrateMissingParticipantProfiles(List<Conversation> conversations) {
        if (apiService == null || conversations == null) {
            return;
        }

        for (Conversation conversation : conversations) {
            Conversation.Participant peer = findPeer(conversation, currentUserId);
            if (!shouldFetchProfile(peer)) {
                continue;
            }

            String peerId = peer.getId();
            if (peerId == null || !requestedProfileIds.add(peerId)) {
                continue;
            }

            apiService.getUserProfile(peerId).enqueue(new Callback<UserProfileResponse>() {
                @Override
                public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }

                    UserProfileResponse profile = response.body();
                    applyProfileToPeer(peerId, profile);
                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        activeAdapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                }
            });
        }
    }

    private boolean shouldFetchProfile(Conversation.Participant peer) {
        if (peer == null || peer.getId() == null || peer.getId().trim().isEmpty()) {
            return false;
        }
        return isBlank(peer.getAvatarUrl()) || isBlank(peer.getUsername());
    }

    private void applyProfileToPeer(String peerId, UserProfileResponse profile) {
        for (Conversation conversation : allConversations) {
            Conversation.Participant participant = findPeer(conversation, currentUserId);
            if (participant == null || participant.getId() == null || !participant.getId().equals(peerId)) {
                continue;
            }

            if (isBlank(participant.getUsername()) && !isBlank(profile.getUsername())) {
                participant.setUsername(profile.getUsername());
            }
            if (isBlank(participant.getAvatarUrl()) && !isBlank(profile.getAvatarUrl())) {
                participant.setAvatarUrl(profile.getAvatarUrl());
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
