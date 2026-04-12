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
import com.example.instabond_fe.utils.RichMessageUtils;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private static final Pattern JSON_URL_PATTERN = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RESPONSE_URL_PATTERN = Pattern.compile("url=([^,\\s)]+)");

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
        private final LinearLayout richMessageLayout;
        private final ImageView ivMessageImage;
        private final ImageView ivRichPreview;
        private final TextView tvMessageContent;
        private final TextView tvRichHeader;
        private final TextView tvRichTitle;
        private final TextView tvRichSubtitle;
        private final TextView tvMessageTime;

        ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            containerBubble = itemView.findViewById(R.id.container_bubble);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
            richMessageLayout = itemView.findViewById(R.id.layout_rich_message);
            ivMessageImage = itemView.findViewById(R.id.iv_message_image);
            ivRichPreview = itemView.findViewById(R.id.iv_rich_preview);
            tvMessageContent = itemView.findViewById(R.id.tv_message_content);
            tvRichHeader = itemView.findViewById(R.id.tv_rich_header);
            tvRichTitle = itemView.findViewById(R.id.tv_rich_title);
            tvRichSubtitle = itemView.findViewById(R.id.tv_rich_subtitle);
            tvMessageTime = itemView.findViewById(R.id.tv_message_time);
        }

        void bind(ChatMessageResponse message, String currentUserId) {
            if (message == null) {
                return;
            }

            boolean isMine = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
            String resolvedImageUrl = resolveImageUrl(message);
            boolean isImage = resolvedImageUrl != null;
            RichMessageUtils.StoryReplyPayload storyReplyPayload = RichMessageUtils.parseStoryReplyPayload(message);
            RichMessageUtils.PostSharePayload postSharePayload = RichMessageUtils.parsePostSharePayload(message);
            boolean isStoryReply = storyReplyPayload != null;
            boolean isPostShare = postSharePayload != null;

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
            tvRichHeader.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    isMine ? android.R.color.white : R.color.feed_meta));
            tvRichTitle.setTextColor(contentColor);
            tvRichSubtitle.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    isMine ? android.R.color.white : R.color.login_text_secondary));

            if (isImage) {
                richMessageLayout.setVisibility(View.GONE);
                Glide.with(itemView).clear(ivRichPreview);
                ivMessageImage.setVisibility(View.VISIBLE);
                Glide.with(itemView)
                        .load(normalizeUrl(resolvedImageUrl))
                        .placeholder(R.drawable.profile_placeholder_bg)
                        .error(R.drawable.profile_placeholder_bg)
                        .into(ivMessageImage);
                tvMessageContent.setVisibility(View.GONE);
            } else if (isStoryReply) {
                bindStoryReply(storyReplyPayload);
            } else if (isPostShare) {
                bindPostShare(postSharePayload);
            } else {
                ivMessageImage.setVisibility(View.GONE);
                richMessageLayout.setVisibility(View.GONE);
                Glide.with(itemView).clear(ivMessageImage);
                Glide.with(itemView).clear(ivRichPreview);
                tvMessageContent.setVisibility(View.VISIBLE);
            }
        }

        private void bindStoryReply(RichMessageUtils.StoryReplyPayload payload) {
            ivMessageImage.setVisibility(View.GONE);
            Glide.with(itemView).clear(ivMessageImage);
            richMessageLayout.setVisibility(View.VISIBLE);
            tvMessageContent.setVisibility(View.GONE);

            tvRichHeader.setText(itemView.getContext().getString(R.string.chat_story_reply_header));
            tvRichTitle.setText(RichMessageUtils.isBlank(payload.getAuthorUsername())
                    ? itemView.getContext().getString(R.string.chat_story_reply_title)
                    : itemView.getContext().getString(R.string.chat_story_reply_title_named, payload.getAuthorUsername()));
            tvRichSubtitle.setText(RichMessageUtils.isBlank(payload.getReplyText())
                    ? itemView.getContext().getString(R.string.chat_story_reply_empty)
                    : payload.getReplyText());

            Glide.with(itemView)
                    .load(normalizeUrl(payload.getMediaUrl()))
                    .placeholder(R.drawable.create_post_preview_placeholder)
                    .error(R.drawable.create_post_preview_placeholder)
                    .into(ivRichPreview);
        }

        private void bindPostShare(RichMessageUtils.PostSharePayload payload) {
            ivMessageImage.setVisibility(View.GONE);
            Glide.with(itemView).clear(ivMessageImage);
            richMessageLayout.setVisibility(View.VISIBLE);
            tvMessageContent.setVisibility(View.GONE);

            tvRichHeader.setText(itemView.getContext().getString(R.string.chat_post_share_header));
            tvRichTitle.setText(RichMessageUtils.isBlank(payload.getUsername())
                    ? itemView.getContext().getString(R.string.chat_post_share_title)
                    : itemView.getContext().getString(R.string.chat_post_share_title_named, payload.getUsername()));
            tvRichSubtitle.setText(RichMessageUtils.isBlank(payload.getCaption())
                    ? itemView.getContext().getString(R.string.chat_post_share_empty)
                    : payload.getCaption());

            Glide.with(itemView)
                    .load(normalizeUrl(payload.getImageUrl()))
                    .placeholder(R.drawable.create_post_preview_placeholder)
                    .error(R.drawable.create_post_preview_placeholder)
                    .into(ivRichPreview);
        }

        private boolean looksLikeUrl(String value) {
            if (value == null) {
                return false;
            }
            String lower = value.trim().toLowerCase(Locale.US);
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/");
        }

        private String resolveImageUrl(ChatMessageResponse message) {
            if (message == null || !"image".equalsIgnoreCase(message.getType())) {
                return null;
            }

            String content = message.getContent();
            if (looksLikeUrl(content)) {
                return content;
            }

            String parsed = parseUrlFromPayload(content);
            return looksLikeUrl(parsed) ? parsed : null;
        }

        private String parseUrlFromPayload(String content) {
            if (content == null || content.trim().isEmpty()) {
                return null;
            }

            Matcher jsonMatcher = JSON_URL_PATTERN.matcher(content);
            if (jsonMatcher.find()) {
                return jsonMatcher.group(1);
            }

            Matcher responseMatcher = RESPONSE_URL_PATTERN.matcher(content);
            if (responseMatcher.find()) {
                return responseMatcher.group(1);
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

        private int dp(int value) {
            return Math.round(itemView.getResources().getDisplayMetrics().density * value);
        }
    }
}
