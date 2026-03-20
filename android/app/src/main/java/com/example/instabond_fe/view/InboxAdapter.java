package com.example.instabond_fe.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.databinding.ItemInboxConversationBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.network.SessionManager;

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
        Conversation conversation = conversations.get(position);
        holder.bind(conversation, currentUserId, clickListener);
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

        void bind(Conversation conversation, String currentUserId, OnConversationClickListener clickListener) {
            String safeCurrentUserId = currentUserId == null ? "" : currentUserId;
            String title = "Unknown";
            String avatarUrl = null;

            // Priority: group name -> if null, use participant username (for 2-user chat) -> fallback to Unknown
            if (conversation.getTitle() != null && !conversation.getTitle().isEmpty()) {
                title = conversation.getTitle();
            } else if (conversation.getParticipants() != null && conversation.getParticipants().size() == 2) {
                for (Conversation.Participant p : conversation.getParticipants()) {
                    if (p.getId() != null && !p.getId().equals(safeCurrentUserId)) {
                        title = p.getUsername() != null ? p.getUsername() : "Unknown";
                        avatarUrl = p.getAvatarUrl();
                        break;
                    }
                }
            }

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(binding.ivAvatar.getContext())
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(com.example.instabond_fe.R.drawable.ic_person)
                        .error(com.example.instabond_fe.R.drawable.ic_person)
                        .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setImageResource(com.example.instabond_fe.R.drawable.ic_person);
            }

            String preview = "No messages yet";
            if (conversation.getLastMessage() != null) {
                String content = conversation.getLastMessage().getContent();
                String safeContent = content == null ? "" : content;
                if (safeCurrentUserId.equals(conversation.getLastMessage().getSenderId())) {
                    preview = "You: " + safeContent;
                } else {
                    preview = safeContent.isEmpty() ? "No messages yet" : safeContent;
                }
            }

            String updatedAt = formatRelativeTime(conversation.getUpdatedAt());

            binding.tvConversationTitle.setText(title);
            binding.tvConversationPreview.setText(preview);
            binding.tvConversationTime.setText(updatedAt);
            binding.getRoot().setOnClickListener(v -> clickListener.onConversationClick(conversation));
        }

        // == UTILITY METHODS ==
        private String formatRelativeTime(String isoString) {
            if (isoString == null || isoString.isEmpty()) return "";
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = sdf.parse(isoString);
                if (date != null) {
                    return android.text.format.DateUtils.getRelativeTimeSpanString(
                            date.getTime(), System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE).toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return isoString;
        }
    }
}
