package com.example.instabond_fe.view;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Post;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface OnPostInteractionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post, int position);
        void onShareClicked(Post post, int position);
        void onUserClicked(Post post, int position);
    }

    private static final String[] LOCATION_FALLBACKS = {
            "MILAN, ITALY",
            "BERLIN, GERMANY",
            "SEOUL, KOREA",
            "TOKYO, JAPAN"
    };

    private static final String[] TIME_FALLBACKS = {
            "2 HOURS AGO",
            "5 HOURS AGO",
            "1 DAY AGO",
            "2 DAYS AGO"
    };

    private final List<Post> posts;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);
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
        holder.tvLocation.setText(LOCATION_FALLBACKS[position % LOCATION_FALLBACKS.length]);
        holder.tvLikeCount.setText(numberFormat.format(post.getLikesCount()));
        holder.tvCommentCount.setText(numberFormat.format(post.getCommentsCount()));
        holder.tvViewComments.setText("View all " + numberFormat.format(post.getCommentsCount()) + " comments");
        holder.tvTimeAgo.setText(TIME_FALLBACKS[position % TIME_FALLBACKS.length]);
        holder.tvCaption.setText(buildCaption(post));
        holder.tvImageCount.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        holder.tvImageCount.setText("1/3");

        int accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.login_bg_start);
        int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.feed_icon_dark);
        holder.btnLike.setColorFilter(post.isLiked() ? accentColor : defaultColor);
        holder.btnComment.setColorFilter(defaultColor);
        holder.btnBookmark.setColorFilter(defaultColor);

        Glide.with(holder.itemView)
                .load(post.getAvatarUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);

        holder.btnLike.setOnClickListener(v -> {
            if (listener != null) listener.onLikeClicked(post, position);
        });
        holder.btnComment.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post, position);
        });
        holder.tvViewComments.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post, position);
        });
        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareClicked(post, position);
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
            holder.tvImageCount.setVisibility(View.GONE);
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

    private CharSequence buildCaption(Post post) {
        String username = post.getUsername() == null ? "" : post.getUsername();
        String caption = post.getCaption() == null ? "" : post.getCaption();
        SpannableStringBuilder builder = new SpannableStringBuilder(username + " " + caption);
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        ImageView ivPostImage;
        TextView tvUsername;
        TextView tvLocation;
        TextView tvLikeCount;
        TextView tvCommentCount;
        TextView tvCaption;
        TextView tvViewComments;
        TextView tvTimeAgo;
        TextView tvImageCount;
        View flPostImage;
        ImageButton btnLike;
        ImageButton btnComment;
        ImageButton btnShare;
        ImageButton btnBookmark;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            ivPostImage = itemView.findViewById(R.id.iv_post_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);
            tvCommentCount = itemView.findViewById(R.id.tv_comment_count);
            tvCaption = itemView.findViewById(R.id.tv_caption);
            tvViewComments = itemView.findViewById(R.id.tv_view_comments);
            tvTimeAgo = itemView.findViewById(R.id.tv_time_ago);
            tvImageCount = itemView.findViewById(R.id.tv_image_count);
            flPostImage = itemView.findViewById(R.id.fl_post_image);
            btnLike = itemView.findViewById(R.id.btn_like);
            btnComment = itemView.findViewById(R.id.btn_comment);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
        }
    }
}
