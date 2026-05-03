package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityInboxBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.model.UserSearchDTO;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.viewmodel.InboxViewModel;
import com.example.instabond_fe.viewmodel.SearchViewModel;

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
    private SearchUserAdapter searchUserAdapter;
    private InboxViewModel viewModel;
    private SearchViewModel searchViewModel;
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
        searchUserAdapter = new SearchUserAdapter();
        searchUserAdapter.setOnItemClickListener(this::openConversationFromSuggestion);

        setupLists();
        bindActions();

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        viewModel.getInboxLiveData().observe(this, conversations -> {
            allConversations.clear();
            if (conversations != null) {
                allConversations.addAll(conversations);
            }
            hydrateMissingParticipantProfiles(allConversations);
            applyFilters();
        });

        searchViewModel.getSuggestionsLiveData().observe(this, suggestions -> runOnUiThread(() -> {
            List<UserSearchDTO> safeSuggestions = suggestions == null ? new ArrayList<>() : suggestions;
            searchUserAdapter.setUsers(safeSuggestions);
        }));

        viewModel.loadInbox();
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.ensureRealtimeConnected();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadInbox();
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

        binding.rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchSuggestions.setAdapter(searchUserAdapter);
    }

    private void bindActions() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnClearSearch.setOnClickListener(v -> clearSearchAndReset());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (query.isEmpty()) {
                    searchViewModel.onSearchQueryChanged("");
                    showDefaultMode();
                    applyFilters();
                } else {
                    showSuggestionsMode();
                    searchViewModel.onSearchQueryChanged(query);
                }
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

        if (!query.isEmpty()) {
            return;
        }

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
        updateActiveSectionVisibility(activeItems);
    }

    private void showSuggestionsMode() {
        binding.rvSearchSuggestions.setVisibility(View.VISIBLE);
        binding.tvActiveHeading.setVisibility(View.GONE);
        binding.rvActiveUsers.setVisibility(View.GONE);
        binding.tvRecentHeading.setVisibility(View.GONE);
        binding.rvInbox.setVisibility(View.GONE);
    }

    private void showDefaultMode() {
        binding.rvSearchSuggestions.setVisibility(View.GONE);
        binding.tvRecentHeading.setVisibility(View.VISIBLE);
        binding.rvInbox.setVisibility(View.VISIBLE);
    }

    private void clearSearchAndReset() {
        binding.etSearch.setText("");
        binding.etSearch.clearFocus();
        hideKeyboard();
        searchViewModel.onSearchQueryChanged("");
        searchUserAdapter.setUsers(new ArrayList<>());
        showDefaultMode();
        applyFilters();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) {
            view = binding.getRoot();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void openConversationFromSuggestion(UserSearchDTO user) {
        if (user == null || user.getId() == null || user.getId().trim().isEmpty()) {
            return;
        }

        apiService.getOrCreateDirectConversation(user.getId()).enqueue(new Callback<Conversation>() {
            @Override
            public void onResponse(Call<Conversation> call, Response<Conversation> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(InboxActivity.this, R.string.chat_missing_conversation, Toast.LENGTH_SHORT).show();
                    return;
                }

                Conversation conversation = response.body();
                Boolean cachedPresence = WebSocketManager.getInstance(InboxActivity.this)
                    .getCachedPresenceByUserId(user.getId());
                boolean partnerOnline = Boolean.TRUE.equals(cachedPresence);
                Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
                intent.putExtra("CONVERSATION_ID", conversation.getId());
                intent.putExtra("conversationId", conversation.getId());

                intent.putExtra("PARTNER_ID", user.getId());
                intent.putExtra("PARTNER_NAME", user.getUsername());
                intent.putExtra("PARTNER_EMAIL", "");
                intent.putExtra("PARTNER_AVATAR", user.getAvatarUrl());
                intent.putExtra("PARTNER_ONLINE", partnerOnline);

                intent.putExtra("partnerId", user.getId());
                intent.putExtra("partnerName", user.getUsername());
                intent.putExtra("partnerEmail", "");
                intent.putExtra("partnerAvatar", user.getAvatarUrl());
                intent.putExtra("partnerOnline", partnerOnline);

                startActivity(intent);
            }

            @Override
            public void onFailure(Call<Conversation> call, Throwable t) {
                Toast.makeText(InboxActivity.this, R.string.chat_missing_conversation, Toast.LENGTH_SHORT).show();
            }
        });
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
        java.util.LinkedHashSet<String> seenPeerIds = new java.util.LinkedHashSet<>();

        for (Conversation conversation : source) {
            Conversation.Participant peer = findPeer(conversation, currentUserId);
            if (peer == null || peer.getId() == null || !seenPeerIds.add(peer.getId())) {
                continue;
            }
            if (peer.isOnline()) {
                onlineFirst.add(conversation);
            }
        }

        return onlineFirst.size() > 6 ? onlineFirst.subList(0, 6) : onlineFirst;
    }

    private void updateActiveSectionVisibility(List<Conversation> activeItems) {
        int visibility = activeItems == null || activeItems.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE;
        binding.tvActiveHeading.setVisibility(visibility);
        binding.rvActiveUsers.setVisibility(visibility);
    }

    private void openConversation(Conversation conversation) {
        markConversationAsReadIfNeeded(conversation);
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

    private void markConversationAsReadIfNeeded(Conversation conversation) {
        if (conversation == null || apiService == null) {
            return;
        }

        com.example.instabond_fe.model.LastMessage lastMessage = conversation.getLastMessage();
        if (lastMessage == null) {
            return;
        }

        String senderId = lastMessage.getSenderId();
        if (senderId == null || senderId.trim().isEmpty()) {
            return;
        }

        String currentId = currentUserId == null ? "" : currentUserId;
        Boolean isRead = lastMessage.getIsRead();
        if (senderId.equals(currentId) || Boolean.TRUE.equals(isRead)) {
            return;
        }

        apiService.markMessagesAsRead(conversation.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
            }
        });
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
