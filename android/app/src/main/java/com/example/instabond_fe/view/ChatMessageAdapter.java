package com.example.instabond_fe.view;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.ChatMessageResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
        private final TextView tvMessageContent;
        private final TextView tvMessageTime;

        ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            containerBubble = itemView.findViewById(R.id.container_bubble);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
            tvMessageContent = itemView.findViewById(R.id.tv_message_content);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }

        void bind(ChatMessageResponse message, String currentUserId) {
            if (message == null) {
                return;
            }

            boolean isMine = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
            tvMessageContent.setText(message.getContent() == null ? "" : message.getContent());
            tvMessageTime.setText(formatTime(message.getCreatedAt()));

            ViewGroup.LayoutParams rawParams = containerBubble.getLayoutParams();
            if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) rawParams;
                if (isMine) {
                    marginParams.setMarginStart(48);
                    marginParams.setMarginEnd(0);
                } else {
                    marginParams.setMarginStart(0);
                    marginParams.setMarginEnd(48);
                }
                containerBubble.setLayoutParams(marginParams);
            }

            containerBubble.setGravity(isMine ? Gravity.END : Gravity.START);
            bubbleCard.setBackgroundResource(isMine ? R.drawable.bg_chat_bubble_me : R.drawable.bg_chat_bubble_peer);

            int contentColor = ContextCompat.getColor(
                    itemView.getContext(),
                    isMine ? R.color.chat_text_me : R.color.chat_text_peer
            );
            int timeColor = ContextCompat.getColor(
                    itemView.getContext(),
                    isMine ? R.color.chat_time_me : R.color.chat_time_peer
            );
            tvMessageContent.setTextColor(contentColor);
            tvMessageTime.setTextColor(timeColor);
            tvMessageTime.setTextAlignment(isMine ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        }

        private String formatTime(String isoString) {
            if (isoString == null || isoString.isEmpty()) {
                return "";
            }
            try {
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                input.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = input.parse(isoString);
                if (parsed != null) {
                    return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed);
                }
            } catch (Exception ignored) {
            }
            return "";
        }
    }
}
