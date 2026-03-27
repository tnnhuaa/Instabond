package com.example.instabond_fe.view;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface OnCommentInteractionListener {
        void onReplyClicked(CommentResponse comment);
        void onLikeClicked(CommentResponse comment, int position);
    }

    private final List<CommentResponse> comments = new ArrayList<>();
    private final String postAuthorUsername;
    private final OnCommentInteractionListener listener;

    public CommentAdapter(String postAuthorUsername, OnCommentInteractionListener listener) {
        this.postAuthorUsername = postAuthorUsername == null ? "" : postAuthorUsername.trim().toLowerCase(Locale.US);
        this.listener = listener;
    }

    public void setComments(List<CommentResponse> newComments) {
        comments.clear();
        if (newComments != null) {
            comments.addAll(newComments);
        }
        notifyDataSetChanged();
    }

    public void addComment(CommentResponse comment) {
        if (comment != null) {
            comments.add(0, comment);
            notifyItemInserted(0);
        }
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        CommentResponse comment = comments.get(position);

        String username = "unknown";
        String avatarUrl = "";
        if (comment.getAuthor() != null) {
            if (comment.getAuthor().getUsername() != null) {
                username = comment.getAuthor().getUsername();
            }
            if (comment.getAuthor().getAvatarUrl() != null) {
                avatarUrl = comment.getAuthor().getAvatarUrl();
            }
        }

        holder.tvUsername.setText(username);
        holder.tvContent.setText(comment.getContent());
        holder.tvTime.setText(TimeUtils.getCompactRelativeTime(comment.getCreatedAt()));
        holder.tvAuthorBadge.setVisibility(isPostAuthor(username) ? View.VISIBLE : View.GONE);
        
        if (comment.getLikesCount() > 0) {
            holder.tvLikeCount.setVisibility(View.VISIBLE);
            holder.tvLikeCount.setText(String.valueOf(comment.getLikesCount()));
        } else {
            holder.tvLikeCount.setVisibility(View.GONE);
        }

        if (comment.isLiked()) {
            holder.ivLike.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.red_500))); // Ensure you have red_500 or use Color.RED
            holder.ivLike.setImageResource(R.drawable.ic_heart_filled); // Assuming you have a filled heart icon, or fallback to tinting
        } else {
            holder.ivLike.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.feed_meta)));
            holder.ivLike.setImageResource(R.drawable.ic_heart);
        }

        Glide.with(holder.itemView)
                .load(normalizeUrl(avatarUrl))
                .circleCrop()
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);

        // Nested reply margin
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (comment.getParentId() != null && !comment.getParentId().isEmpty()) {
            layoutParams.setMarginStart(dpToPx(holder.itemView, 56)); // indent for replies (40dp avatar + 16dp margin)
        } else {
            layoutParams.setMarginStart(0);
        }
        holder.itemView.setLayoutParams(layoutParams);

        if (listener != null) {
            holder.tvReply.setOnClickListener(v -> listener.onReplyClicked(comment));
            holder.ivLike.setOnClickListener(v -> listener.onLikeClicked(comment, position));
        }
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    private boolean isPostAuthor(String username) {
        return !postAuthorUsername.isEmpty()
                && username != null
                && postAuthorUsername.equals(username.trim().toLowerCase(Locale.US));
    }

    private int dpToPx(View view, int dp) {
        return (int) (dp * view.getResources().getDisplayMetrics().density);
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

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        ImageView ivLike;
        TextView tvUsername;
        TextView tvAuthorBadge;
        TextView tvContent;
        TextView tvTime;
        TextView tvLikeCount;
        TextView tvReply;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
            ivLike = itemView.findViewById(R.id.iv_comment_like);
            tvUsername = itemView.findViewById(R.id.tv_comment_username);
            tvAuthorBadge = itemView.findViewById(R.id.tv_comment_author_badge);
            tvContent = itemView.findViewById(R.id.tv_comment_content);
            tvTime = itemView.findViewById(R.id.tv_comment_time);
            tvLikeCount = itemView.findViewById(R.id.tv_comment_like_count);
            tvReply = itemView.findViewById(R.id.tv_comment_reply);
        }
    }
}
