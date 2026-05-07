package com.example.instabond_fe.view;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.SuggestedTag;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.TimeUtils;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    private static final String AUDIO_MUTED_ICON = "\uD83D\uDD07";
    private static final String AUDIO_PLAYING_ICON = "\uD83D\uDD08";
    private static MediaPlayer activePlayer;
    private static String activePostId;
    private static WeakReference<PostAdapter> activeAdapterRef;

    public interface OnPostInteractionListener {
        void onLikeClicked(Post post, int position);
        void onCommentClicked(Post post, int position);
        void onShareClicked(Post post, int position);
        void onBookmarkClicked(Post post, int position);
        void onUserClicked(Post post, int position);
        void onOptionsClicked(Post post, int position, View anchorView);
    }

    private static final int[] TIME_FALLBACKS = {
            R.string.feed_time_fallback_two_hours,
            R.string.feed_time_fallback_five_hours,
            R.string.feed_time_fallback_one_day,
            R.string.feed_time_fallback_two_days
    };

    private final List<Post> posts;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    private OnPostInteractionListener listener;
    private boolean showOverflowActions;

    public PostAdapter(List<Post> posts) {
        this.posts = new ArrayList<>(posts);
    }

    public void setListener(OnPostInteractionListener listener) {
        this.listener = listener;
    }

    public void setShowOverflowActions(boolean showOverflowActions) {
        this.showOverflowActions = showOverflowActions;
    }

    public void setPosts(List<Post> newPosts) {
        posts.clear();
        if (newPosts != null) {
            posts.addAll(newPosts);
        }
        releaseAudioIfMissingFromAdapter();
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

    public void removePostAt(int position) {
        if (position < 0 || position >= posts.size()) {
            return;
        }
        posts.remove(position);
        releaseAudioIfMissingFromAdapter();
        notifyItemRemoved(position);
    }

    public boolean removePostById(String postId) {
        if (postId == null || postId.trim().isEmpty()) {
            return false;
        }

        for (int index = 0; index < posts.size(); index++) {
            Post post = posts.get(index);
            if (post != null && postId.equals(post.getId())) {
                posts.remove(index);
                releaseAudioIfMissingFromAdapter();
                notifyItemRemoved(index);
                return true;
            }
        }
        return false;
    }

    public boolean removePostsByIds(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return false;
        }

        boolean removed = false;
        for (String postId : new ArrayList<>(postIds)) {
            removed = removePostById(postId) || removed;
        }
        return removed;
    }

    public void notifyPostChanged(Post post) {
        if (post == null) {
            return;
        }
        int index = posts.indexOf(post);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        Context context = holder.itemView.getContext();
        holder.tvUsername.setText(post.getUsername());

        String postMeta = buildPostMeta(post);
        holder.tvLocation.setText(postMeta);
        holder.tvLocation.setVisibility(postMeta.isEmpty() ? View.GONE : View.VISIBLE);

        holder.tvLikeCount.setText(numberFormat.format(post.getLikesCount()));
        holder.tvCommentCount.setText(numberFormat.format(post.getCommentsCount()));
        holder.tvViewComments.setText(post.getCommentsCount() <= 0
                ? context.getString(R.string.feed_no_comments)
                : context.getString(R.string.feed_view_all_comments, numberFormat.format(post.getCommentsCount())));
        holder.tvTimeAgo.setText(buildFeedTimeLabel(context, post.getCreatedAt(), position));
        holder.tvCaption.setText(buildCaption(post));

        int accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.login_bg_start);
        int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.feed_icon_dark);
        holder.btnLike.setColorFilter(post.isLiked() ? accentColor : defaultColor);
        holder.btnComment.setColorFilter(defaultColor);
        holder.btnBookmark.setColorFilter(post.isBookmarked() ? accentColor : defaultColor);

        AvatarLoader.load(holder.ivAvatar, post.getAvatarUrl());

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
        holder.btnBookmark.setOnClickListener(v -> {
            if (listener != null) listener.onBookmarkClicked(post, position);
        });
        boolean canShowOverflow = showOverflowActions && post.isOwnedByCurrentUser();
        holder.btnOverflow.setVisibility(canShowOverflow ? View.VISIBLE : View.GONE);
        holder.btnOverflow.setOnClickListener(canShowOverflow && listener != null
                ? v -> listener.onOptionsClicked(post, position, v)
                : null);

        if (post.getImageUrl() == null || post.getImageUrl().trim().isEmpty()) {
            holder.flPostImage.setVisibility(View.GONE);
            Glide.with(holder.itemView).clear(holder.ivPostImage);
            holder.ivPostImage.setImageDrawable(null);
            holder.tvAudioToggle.setVisibility(View.GONE);
            return;
        }

        holder.flPostImage.setVisibility(View.VISIBLE);
        Glide.with(holder.itemView)
                .load(post.getImageUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivPostImage);

        renderBadges(holder.flPostImage, post.getTaggedUsers(), post.isTagsVisible(), context);

        bindAudioToggle(holder, post);

        GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                post.setTagsVisible(!post.isTagsVisible());
                renderBadges(holder.flPostImage, post.getTaggedUsers(), post.isTagsVisible(), context);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!post.isLiked() && listener != null) {
                    listener.onLikeClicked(post, position);
                }
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        holder.flPostImage.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void renderBadges(FrameLayout container, List<SuggestedTag> tags, boolean isVisible, Context context) {
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            if ("USER_TAG_BADGE".equals(child.getTag())) {
                container.removeViewAt(i);
            }
        }

        if (!isVisible || tags == null || tags.isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (SuggestedTag user : tags) {
            if (user.getPosition() == null) continue;

            TextView badge = (TextView) inflater.inflate(R.layout.item_tag_user_badge, container, false);
            badge.setText(user.getUsername());
            badge.setTag("USER_TAG_BADGE");

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

            badge.setVisibility(View.INVISIBLE);
            container.addView(badge, params);

            badge.post(() -> {
                int containerWidth = container.getWidth();
                int containerHeight = container.getHeight();

                float tapX = (float) (user.getPosition().getX() * containerWidth);
                float tapY = (float) (user.getPosition().getY() * containerHeight);

                badge.setX(tapX - (badge.getWidth() / 2f));
                badge.setY(tapY - (badge.getHeight() / 2f));

                if (badge.getX() < 0) badge.setX(0);
                if (badge.getX() + badge.getWidth() > containerWidth) {
                    badge.setX(containerWidth - badge.getWidth());
                }
                if (badge.getY() < 0) badge.setY(0);
                if (badge.getY() + badge.getHeight() > containerHeight) {
                    badge.setY(containerHeight - badge.getHeight());
                }
                badge.setVisibility(View.VISIBLE);
            });

            badge.setOnClickListener(v -> {
            });
        }
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public static void stopAudioPlayback() {
        stopActiveAudio();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        PostAdapter activeAdapter = activeAdapterRef != null ? activeAdapterRef.get() : null;
        if (activeAdapter == this) {
            stopActiveAudio();
        }
    }

    private CharSequence buildCaption(Post post) {
        String username = post.getUsername() == null ? "" : post.getUsername();
        String caption = post.getCaption() == null ? "" : post.getCaption();
        SpannableStringBuilder builder = new SpannableStringBuilder(username + " " + caption);
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private String buildFeedTimeLabel(Context context, String createdAt, int position) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return context.getString(TIME_FALLBACKS[position % TIME_FALLBACKS.length]);
        }

        String compactTime = TimeUtils.getCompactRelativeTime(createdAt);
        if ("JUST NOW".equals(compactTime)) {
            return context.getString(R.string.feed_time_just_now);
        }
        if (compactTime.endsWith("M AGO")) {
            String value = compactTime.replace("M AGO", "").trim();
            return context.getString(R.string.feed_time_minutes_ago, value, value);
        }
        if (compactTime.endsWith("H AGO")) {
            String value = compactTime.replace("H AGO", "").trim();
            return context.getString(R.string.feed_time_hours_ago, value, value);
        }
        if (compactTime.endsWith("D AGO")) {
            String value = compactTime.replace("D AGO", "").trim();
            return context.getString(R.string.feed_time_days_ago, value, value);
        }
        return compactTime;
    }

    private String buildPostMeta(Post post) {
        String music = valueOrEmpty(post.getMusicSummary());
        String location = valueOrEmpty(post.getLocation());

        if (music.isEmpty()) {
            return location;
        }
        if (location.isEmpty()) {
            return music;
        }
        return music + " - " + location;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void bindAudioToggle(PostViewHolder holder, Post post) {
        if (!post.isHasMusicBadge() || valueOrEmpty(post.getMusicPreviewUrl()).isEmpty()) {
            holder.tvAudioToggle.setVisibility(View.GONE);
            holder.tvAudioToggle.setOnClickListener(null);
            return;
        }

        holder.tvAudioToggle.setVisibility(View.VISIBLE);
        boolean isPlaying = isPostPlaying(post);
        holder.tvAudioToggle.setText(isPlaying ? AUDIO_PLAYING_ICON : AUDIO_MUTED_ICON);
        holder.tvAudioToggle.setContentDescription(isPlaying ? "Mute preview audio" : "Play preview audio");
        holder.tvAudioToggle.setOnClickListener(v -> toggleAudio(post));
    }

    private void toggleAudio(Post post) {
        if (post == null || valueOrEmpty(post.getMusicPreviewUrl()).isEmpty()) {
            return;
        }

        if (isPostPlaying(post)) {
            stopActiveAudio();
            return;
        }

        startAudio(post);
    }

    private void startAudio(Post post) {
        String newPostId = post.getId();
        String previewUrl = valueOrEmpty(post.getMusicPreviewUrl());
        if (newPostId.isEmpty() || previewUrl.isEmpty()) {
            return;
        }

        String previousPostId = activePostId;
        PostAdapter previousAdapter = activeAdapterRef != null ? activeAdapterRef.get() : null;
        stopPlayerOnly();
        activePostId = newPostId;
        activeAdapterRef = new WeakReference<>(this);

        MediaPlayer player = new MediaPlayer();
        activePlayer = player;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        try {
            player.setDataSource(previewUrl);
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnCompletionListener(mp -> stopActiveAudio());
            player.setOnErrorListener((mp, what, extra) -> {
                stopActiveAudio();
                return true;
            });
            player.prepareAsync();
        } catch (Exception exception) {
            stopActiveAudio();
            return;
        }

        notifyAudioStateChanged(previousAdapter, previousPostId);
        notifyAudioStateChanged(this, newPostId);
    }

    private static void stopActiveAudio() {
        PostAdapter previousAdapter = activeAdapterRef != null ? activeAdapterRef.get() : null;
        String previousPostId = activePostId;
        stopPlayerOnly();
        activePostId = null;
        activeAdapterRef = null;
        notifyAudioStateChanged(previousAdapter, previousPostId);
    }

    private static void stopPlayerOnly() {
        if (activePlayer == null) {
            return;
        }

        try {
            if (activePlayer.isPlaying()) {
                activePlayer.stop();
            }
        } catch (IllegalStateException ignored) {
        }

        activePlayer.reset();
        activePlayer.release();
        activePlayer = null;
    }

    private boolean isPostPlaying(Post post) {
        return post != null
                && activePlayer != null
                && activePostId != null
                && activePostId.equals(post.getId());
    }

    private void releaseAudioIfMissingFromAdapter() {
        if (activePostId == null) {
            return;
        }

        for (Post post : posts) {
            if (post != null && activePostId.equals(post.getId())) {
                return;
            }
        }

        PostAdapter activeAdapter = activeAdapterRef != null ? activeAdapterRef.get() : null;
        if (activeAdapter == this) {
            stopActiveAudio();
        }
    }

    private static void notifyAudioStateChanged(PostAdapter adapter, String postId) {
        if (adapter == null || postId == null || postId.trim().isEmpty()) {
            return;
        }

        for (int index = 0; index < adapter.posts.size(); index++) {
            Post post = adapter.posts.get(index);
            if (post != null && postId.equals(post.getId())) {
                adapter.notifyItemChanged(index);
                break;
            }
        }
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
        FrameLayout flPostImage;
        TextView tvAudioToggle;
        ImageButton btnLike;
        ImageButton btnComment;
        ImageButton btnShare;
        ImageButton btnBookmark;
        ImageButton btnOverflow;

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
            flPostImage = itemView.findViewById(R.id.fl_post_image);
            tvAudioToggle = itemView.findViewById(R.id.tv_audio_toggle);
            btnLike = itemView.findViewById(R.id.btn_like);
            btnComment = itemView.findViewById(R.id.btn_comment);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnOverflow = itemView.findViewById(R.id.btn_overflow);
        }
    }
}
