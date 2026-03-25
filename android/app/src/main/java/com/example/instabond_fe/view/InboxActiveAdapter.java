package com.example.instabond_fe.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class InboxActiveAdapter extends RecyclerView.Adapter<InboxActiveAdapter.ActiveUserViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    private final List<Conversation> conversations = new ArrayList<>();
    private final String currentUserId;
    private final OnConversationClickListener clickListener;

    public InboxActiveAdapter(Context context, OnConversationClickListener clickListener) {
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
    public ActiveUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inbox_active_user, parent, false);
        return new ActiveUserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActiveUserViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.bind(conversation, currentUserId, clickListener, position);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ActiveUserViewHolder extends RecyclerView.ViewHolder {
        private final View avatarRing;
        private final ImageView ivAvatar;
        private final View onlineDot;
        private final TextView tvName;

        ActiveUserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarRing = itemView.findViewById(R.id.view_active_ring);
            ivAvatar = itemView.findViewById(R.id.iv_active_avatar);
            onlineDot = itemView.findViewById(R.id.view_online_dot);
            tvName = itemView.findViewById(R.id.tv_active_name);
        }

        void bind(Conversation conversation,
                  String currentUserId,
                  OnConversationClickListener clickListener,
                  int position) {
            Conversation.Participant peer = findPeer(conversation, currentUserId);
            String name = peer != null && peer.getUsername() != null ? peer.getUsername() : "Unknown";
            String avatarUrl = peer != null ? peer.getAvatarUrl() : "";
            boolean isOnline = peer != null && peer.isOnline();

            tvName.setText(name);
            onlineDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
            avatarRing.setBackgroundResource(position == 0
                    ? R.drawable.comment_author_ring_gradient
                    : R.drawable.bg_active_avatar_ring_neutral);

            Glide.with(itemView)
                    .load(normalizeUrl(avatarUrl))
                    .circleCrop()
                    .placeholder(R.drawable.profile_placeholder_bg)
                    .error(R.drawable.profile_placeholder_bg)
                    .into(ivAvatar);

            itemView.setOnClickListener(v -> clickListener.onConversationClick(conversation));
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
    }
}
