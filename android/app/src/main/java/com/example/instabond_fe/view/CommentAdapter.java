package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.CommentResponse;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private final List<CommentResponse> comments = new ArrayList<>();

    public void setComments(List<CommentResponse> newComments) {
        comments.clear();
        if (newComments != null) {
            comments.addAll(newComments);
        }
        notifyDataSetChanged();
    }

    public void addComment(CommentResponse comment) {
        if (comment != null) {
            comments.add(0, comment); // Add to top
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
            username = comment.getAuthor().getUsername() != null ? comment.getAuthor().getUsername() : "unknown";
            avatarUrl = comment.getAuthor().getAvatarUrl() != null ? comment.getAuthor().getAvatarUrl() : "";
        }

        holder.tvUsername.setText(username);
        holder.tvContent.setText(comment.getContent());
        // Use TimeUtils to format the creation time
        String relativeTime = com.example.instabond_fe.utils.TimeUtils.getRelativeTime(comment.getCreatedAt());
        holder.tvTime.setText(relativeTime); 

        Glide.with(holder.itemView)
                .load(avatarUrl)
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        TextView tvContent;
        TextView tvTime;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
            tvUsername = itemView.findViewById(R.id.tv_comment_username);
            tvContent = itemView.findViewById(R.id.tv_comment_content);
            tvTime = itemView.findViewById(R.id.tv_comment_time);
        }
    }
}
