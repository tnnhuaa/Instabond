package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Post;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface OnPostInteractionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post, int position);
        void onShareClicked(Post post, int position);
        void onUserClicked(Post post, int position);
    }

    private final List<Post> posts;
    private OnPostInteractionListener listener;

    public PostAdapter(List<Post> posts) {
        this.posts = new ArrayList<>(posts);
    }

    public void setListener(OnPostInteractionListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<Post> newPosts) {
        posts.clear();
        if (newPosts != null) {
            posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }

    public void appendPosts(List<Post> morePosts) {
        if (morePosts == null || morePosts.isEmpty()) {
            return;
        }
        int start = posts.size();
        posts.addAll(morePosts);
        notifyItemRangeInserted(start, morePosts.size());
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.tvUsername.setText(post.getUsername());
        holder.tvLikes.setText(post.getLikesCount() + " lượt thích");
        holder.tvCaption.setText(post.getUsername() + "  " + post.getCaption());
        holder.btnViewComments.setText(
                "Xem " + post.getCommentsCount() + " bình luận • "
                        + post.getSharesCount() + " lượt chia sẻ"
        );
        holder.flMusicBadge.setVisibility(post.isHasMusicBadge() ? View.VISIBLE : View.GONE);

        // Update like icon based on state
        if (post.isLiked()) {
            holder.btnLike.setImageResource(R.drawable.ic_heart);
            holder.btnLike.setColorFilter(0xFFE53935); // Red tint when liked
        } else {
            holder.btnLike.setImageResource(R.drawable.ic_heart);
            holder.btnLike.clearColorFilter();
        }

        Glide.with(holder.itemView)
                .load(post.getAvatarUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);

        // Click listeners
        holder.btnLike.setOnClickListener(v -> {
            if (listener != null) listener.onLikeClicked(post, position);
        });
        holder.btnComment.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post, position);
        });
        holder.btnViewComments.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post, position);
        });
        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareClicked(post, position);
        });
        holder.btnBookmark.setOnClickListener(v -> {
            // Bookmark functionality to be implemented
        });
        holder.ivAvatar.setOnClickListener(v -> {
            if (listener != null) listener.onUserClicked(post, position);
        });
        holder.tvUsername.setOnClickListener(v -> {
            if (listener != null) listener.onUserClicked(post, position);
        });

        if (post.getImageUrl() == null || post.getImageUrl().trim().isEmpty()) {
            holder.flPostImage.setVisibility(View.GONE);
            Glide.with(holder.itemView).clear(holder.ivPostImage);
            holder.ivPostImage.setImageDrawable(null);
            return;
        }

        holder.flPostImage.setVisibility(View.VISIBLE);
        Glide.with(holder.itemView)
                .load(post.getImageUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivPostImage);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        ImageView ivPostImage;
        TextView tvUsername;
        TextView tvLikes;
        TextView tvCaption;
        Button btnViewComments;
        View flPostImage;
        View flMusicBadge;
        ImageButton btnLike;
        ImageButton btnComment;
        ImageButton btnShare;
        ImageButton btnBookmark;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            ivPostImage = itemView.findViewById(R.id.iv_post_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvCaption = itemView.findViewById(R.id.tv_caption);
            btnViewComments = itemView.findViewById(R.id.btn_view_comments);
            flPostImage = itemView.findViewById(R.id.fl_post_image);
            flMusicBadge = itemView.findViewById(R.id.fl_music_badge);
            btnLike = itemView.findViewById(R.id.btn_like);
            btnComment = itemView.findViewById(R.id.btn_comment);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
        }
    }
}
