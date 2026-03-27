package com.example.instabond_fe.view;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private final List<ChatMessageResponse> items = new ArrayList<>();
    private final String currentUserId;

    public ChatMessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId == null ? "" : currentUserId;
    }

    public void submitList(List<ChatMessageResponse> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View root = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatMessageViewHolder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        holder.bind(items.get(position), currentUserId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout containerBubble;
        private final LinearLayout bubbleCard;
        private final ImageView ivMessageImage;
        private final TextView tvMessageContent;
        private final TextView tvMessageTime;

        ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            containerBubble = itemView.findViewById(R.id.container_bubble);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
            tvMessageContent = itemView.findViewById(R.id.tv_message_content);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }

        void bind(ChatMessageResponse message, String currentUserId) {
            if (message == null) {
                return;
            }

            boolean isMine = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
            boolean isImage = "image".equalsIgnoreCase(message.getType()) && looksLikeUrl(message.getContent());

            tvMessageTime.setText(TimeUtils.getChatClockLabel(message.getCreatedAt()));
            tvMessageContent.setText(message.getContent() == null ? "" : message.getContent());

            ViewGroup.LayoutParams rawParams = containerBubble.getLayoutParams();
            if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) rawParams;
                if (isMine) {
                    marginParams.setMarginStart(dp(56));
                    marginParams.setMarginEnd(0);
                } else {
                    marginParams.setMarginStart(0);
                    marginParams.setMarginEnd(dp(56));
                }
                containerBubble.setLayoutParams(marginParams);
            }

            containerBubble.setGravity(isMine ? Gravity.END : Gravity.START);
            bubbleCard.setBackgroundResource(isMine
                    ? R.drawable.bg_chat_message_outgoing
                    : R.drawable.bg_chat_message_incoming);

            int contentColor = ContextCompat.getColor(
                    itemView.getContext(),
                    isMine ? android.R.color.white : R.color.login_text_primary);
            tvMessageContent.setTextColor(contentColor);
            tvMessageTime.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.feed_meta));
            tvMessageTime.setTextAlignment(isMine ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);

            if (isImage) {
                ivMessageImage.setVisibility(View.VISIBLE);
                Glide.with(itemView)
                        .load(normalizeUrl(message.getContent()))
                        .placeholder(R.drawable.profile_placeholder_bg)
                        .error(R.drawable.profile_placeholder_bg)
                        .into(ivMessageImage);
                tvMessageContent.setVisibility(View.GONE);
            } else {
                ivMessageImage.setVisibility(View.GONE);
                Glide.with(itemView).clear(ivMessageImage);
                tvMessageContent.setVisibility(View.VISIBLE);
            }
        }

        private boolean looksLikeUrl(String value) {
            if (value == null) {
                return false;
            }
            String lower = value.trim().toLowerCase(Locale.US);
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/");
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

        private int dp(int value) {
            return Math.round(itemView.getResources().getDisplayMetrics().density * value);
        }
    }
}