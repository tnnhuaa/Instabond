package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.databinding.ActivityInboxBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.viewmodel.InboxViewModel;

public class InboxActivity extends AppCompatActivity {

    private static final int LOAD_MORE_THRESHOLD = 4;

    private ActivityInboxBinding binding;
    private InboxAdapter adapter;
    private InboxViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SessionManager sessionManager = new SessionManager(this);
        String currentUserId = sessionManager.getUserId();

        adapter = new InboxAdapter(this, conversation -> {
            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);

            // Original keys to prevent "Conversation missing" crash
            intent.putExtra("CONVERSATION_ID", conversation.getId());
            // Fallback key to ensure ChatViewModel receives the data
            intent.putExtra("conversationId", conversation.getId());

            Conversation.Participant peer = findPeer(conversation, currentUserId);
            if (peer != null) {
                // Original keys
                intent.putExtra("PARTNER_ID", peer.getId());
                intent.putExtra("PARTNER_NAME", peer.getUsername());
                intent.putExtra("PARTNER_EMAIL", peer.getEmail());
                intent.putExtra("PARTNER_AVATAR", peer.getAvatarUrl());
                // TODO: Refactor Online Status
                // intent.putExtra("PARTNER_ONLINE", peer.isOnline());

                // Fallback keys for ChatViewModel matching logic
                intent.putExtra("partnerId", peer.getId());
                intent.putExtra("partnerEmail", peer.getEmail());
            }

            startActivity(intent);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvInbox.setLayoutManager(layoutManager);
        binding.rvInbox.setAdapter(adapter);
        binding.rvInbox.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int totalCount = adapter.getItemCount();
                if (lastVisible >= totalCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadNextPageIfNeeded();
                }
            }
        });

        binding.btnBack.setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        viewModel.getInboxLiveData().observe(this, conversations -> {
            runOnUiThread(() -> {
                adapter.setConversations(conversations);
                adapter.notifyDataSetChanged();
            });
        });

        viewModel.loadInbox();
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.ensureRealtimeConnected();
    }

    private Conversation.Participant findPeer(Conversation conversation, String currentUserId) {
        if (conversation == null || conversation.getParticipants() == null) {
            return null;
        }

        String safeCurrentUserId = currentUserId == null ? "" : currentUserId;
        for (Conversation.Participant participant : conversation.getParticipants()) {
            if (participant.getId() == null || !participant.getId().equals(safeCurrentUserId)) {
                return participant;
            }
        }
        return null;
    }
}
