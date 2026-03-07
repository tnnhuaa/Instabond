package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Post;

import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final List<Post> posts;

    public PostAdapter(List<Post> posts) {
        this.posts = posts;
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
        holder.btnViewComments.setText("Xem " + post.getCommentsCount() + " bình luận");
        holder.flMusicBadge.setVisibility(post.isHasMusicBadge() ? View.VISIBLE : View.GONE);
        if (post.getAvatarResId() != 0) {
            holder.ivAvatar.setImageResource(post.getAvatarResId());
        }
        if (post.getImageResId() != 0) {
            holder.ivPostImage.setImageResource(post.getImageResId());
        }
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
        View flMusicBadge;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            ivPostImage = itemView.findViewById(R.id.iv_post_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvCaption = itemView.findViewById(R.id.tv_caption);
            btnViewComments = itemView.findViewById(R.id.btn_view_comments);
            flMusicBadge = itemView.findViewById(R.id.fl_music_badge);
        }
    }
}

