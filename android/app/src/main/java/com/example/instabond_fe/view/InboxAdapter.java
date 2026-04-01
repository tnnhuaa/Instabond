package com.example.instabond_fe.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ItemInboxConversationBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.InboxViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    private final List<Conversation> conversations = new ArrayList<>();
    private final String currentUserId;
    private final OnConversationClickListener clickListener;

    public InboxAdapter(Context context, OnConversationClickListener clickListener) {
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        String userId = sessionManager.getUserId();
        this.currentUserId = userId == null ? "" : userId;
        this.clickListener = clickListener;
    }

    public void setConversations(List<Conversation> newData) {
        conversations.clear();
        if (newData != null) {
            conversations.addAll(newData);
        }
    }

    @NonNull
    @Override
    public InboxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemInboxConversationBinding binding = ItemInboxConversationBinding.inflate(inflater, parent, false);
        return new InboxViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull InboxViewHolder holder, int position) {
        holder.bind(conversations.get(position), currentUserId, clickListener, position);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class InboxViewHolder extends RecyclerView.ViewHolder {
        private final ItemInboxConversationBinding binding;

        InboxViewHolder(ItemInboxConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Conversation conversation,
                  String currentUserId,
                  OnConversationClickListener clickListener,
                  int position) {
            String safeCurrentUserId = currentUserId == null ? "" : currentUserId;
            Conversation.Participant peer = findPeer(conversation, safeCurrentUserId);

            String title = peer != null && peer.getUsername() != null
                    ? peer.getUsername()
                    : (conversation.getTitle() == null ? "Unknown" : conversation.getTitle());
            String avatarUrl = peer != null ? peer.getAvatarUrl() : "";

            boolean isUnread = conversation.getLastMessage() != null
                    && !safeCurrentUserId.equals(conversation.getLastMessage().getSenderId())
                    && !Boolean.TRUE.equals(conversation.getLastMessage().getIsRead());

            String preview = itemView.getContext().getString(R.string.inbox_empty_preview);
            if (conversation.getLastMessage() != null) {
                String content = conversation.getLastMessage().getContent();
                String safeContent = content == null ? "" : content;
                if (safeCurrentUserId.equals(conversation.getLastMessage().getSenderId())) {
                    preview = safeContent.isEmpty() ? preview : "You: " + safeContent;
                } else {
                    preview = safeContent.isEmpty() ? preview : safeContent;
                }
            }

            AvatarLoader.load(binding.ivAvatar, normalizeUrl(avatarUrl));

            binding.tvConversationTitle.setText(title);
            binding.tvConversationPreview.setText(preview);
            binding.tvConversationTime.setText(TimeUtils.getConversationTimeLabel(conversation.getUpdatedAt()));

            int primarySurface = ContextCompat.getColor(itemView.getContext(), android.R.color.white);
            int secondarySurface = ContextCompat.getColor(itemView.getContext(), R.color.feed_surface);
            int titleColor = ContextCompat.getColor(itemView.getContext(), R.color.login_text_primary);
            int previewColor = ContextCompat.getColor(itemView.getContext(), isUnread ? R.color.login_text_primary : R.color.login_text_secondary);
            int timeColor = ContextCompat.getColor(itemView.getContext(), isUnread ? R.color.login_bg_start : R.color.login_text_secondary);

            binding.cardConversation.setCardBackgroundColor(isUnread || position == 0 ? primarySurface : secondarySurface);
            binding.cardConversation.setCardElevation(isUnread || position == 0 ? dp(8f) : 0f);
            binding.tvConversationTitle.setTextColor(titleColor);
            binding.tvConversationPreview.setTextColor(previewColor);
            binding.tvConversationTime.setTextColor(timeColor);

            binding.viewUnreadBadge.setVisibility(isUnread ? View.VISIBLE : View.GONE);
            binding.viewUnreadBadge.setText("1");

            binding.getRoot().setOnClickListener(v -> clickListener.onConversationClick(conversation));
        }

        private Conversation.Participant findPeer(Conversation conversation, String safeCurrentUserId) {
            if (conversation == null || conversation.getParticipants() == null) {
                return null;
            }
            for (Conversation.Participant participant : conversation.getParticipants()) {
                if (participant.getId() == null || !participant.getId().equals(safeCurrentUserId)) {
                    return participant;
                }
            }
            return null;
        }

        private String normalizeUrl(String rawUrl) {
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                return "";
            }

            android.net.Uri uri = android.net.Uri.parse(rawUrl);
            if (uri.getScheme() != null) {
                return rawUrl;
            }

            String baseUrl = ApiClient.getBaseUrl();
            if (rawUrl.startsWith("/")) {
                return baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1) + rawUrl
                        : baseUrl + rawUrl;
            }
            return baseUrl.endsWith("/") ? baseUrl + rawUrl : baseUrl + "/" + rawUrl;
        }

        private float dp(float value) {
            return value * itemView.getResources().getDisplayMetrics().density;
        }
    }
}
