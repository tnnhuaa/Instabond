package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.model.CreateCommentRequest;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.utils.ShareUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentActivity extends AppCompatActivity implements CommentAdapter.OnCommentInteractionListener {
    private static final String ENTRY_TYPE_TAG = "TAG";
    private static final String EXTRA_ENTRY_TYPE = "entryType";
    private static final String EXTRA_FALLBACK_USER_ID = "fallbackUserId";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private String postId;
    private String entryType;
    private String fallbackUserId;

    private ApiService apiService;
    private SessionManager sessionManager;
    private CommentAdapter commentAdapter;
    private PostAdapter postAdapter;
    private ConcatAdapter contentAdapter;
    private String replyTargetId = null;
    private int loadingRequests = 0;

    private RecyclerView rvComments;
    private EditText etComment;
    private TextView btnPostComment;
    private ProgressBar progressBar;
    private ImageView ivCurrentUserAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        postId = getIntent().getStringExtra("postId");
        entryType = getIntent().getStringExtra(EXTRA_ENTRY_TYPE);
        fallbackUserId = getIntent().getStringExtra(EXTRA_FALLBACK_USER_ID);

        if (postId == null || postId.trim().isEmpty()) {
            Toast.makeText(this, R.string.comment_missing_post, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);
        bindViews();
        setupContentList();
        setupActions();
        loadCurrentUser();
        loadPostDetail();
        loadComments();

        handleFocusRequest();
    }

    private void handleFocusRequest() {
        if (getIntent().getBooleanExtra("focusComment", false)) {
            etComment.requestFocus();
            etComment.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etComment, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);
        }
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        rvComments = findViewById(R.id.rv_comments);
        etComment = findViewById(R.id.et_comment);
        btnPostComment = findViewById(R.id.btn_post_comment);
        progressBar = findViewById(R.id.progress_bar);
        ivCurrentUserAvatar = findViewById(R.id.iv_current_user_avatar);

        TextView[] emojiButtons = new TextView[]{
                findViewById(R.id.emoji_heart),
                findViewById(R.id.emoji_raise_hands),
                findViewById(R.id.emoji_sparkles),
                findViewById(R.id.emoji_fire),
                findViewById(R.id.emoji_heart_eyes),
                findViewById(R.id.emoji_clap)
        };
        String[] emojis = new String[]{"❤️", "🙌", "✨", "🔥", "😍", "👏"};
        for (int i = 0; i < emojiButtons.length; i++) {
            final String emoji = emojis[i];
            emojiButtons[i].setOnClickListener(v -> appendEmoji(emoji));
        }
    }

    private void setupContentList() {
        postAdapter = new PostAdapter(new ArrayList<>());
        commentAdapter = new CommentAdapter("", this);
        contentAdapter = new ConcatAdapter(postAdapter, commentAdapter);

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(contentAdapter);
        rvComments.setClipToPadding(false);

        postAdapter.setListener(new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(Post post, int position) {
                togglePostLike(post, position);
            }

            @Override
            public void onCommentClicked(Post post, int position) {
                etComment.requestFocus();
            }

            @Override
            public void onShareClicked(Post post, int position) {
                apiService.sharePost(post.getId()).enqueue(new Callback<PostResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PostResponse> call,
                                           @NonNull Response<PostResponse> response) {
                        if (response.isSuccessful()) {
                            post.setSharesCount(post.getSharesCount() + 1);
                            postAdapter.notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PostResponse> call, @NonNull Throwable t) {
                    }
                });

                ShareUtils.showShareBottomSheet(
                        CommentActivity.this,
                        post,
                        apiService,
                        sessionManager.getUserId()
                );
            }

            @Override
            public void onUserClicked(Post post, int position) {
                if (post.getAuthorId() == null || post.getAuthorId().trim().isEmpty()) {
                    return;
                }

                Intent intent = new Intent(CommentActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", post.getAuthorId());
                startActivity(intent);
            }

            @Override
            public void onBookmarkClicked(Post post, int position) {
                boolean isCurrentlyBookmarked = post.isBookmarked();
                post.setBookmarked(!isCurrentlyBookmarked);
                postAdapter.notifyItemChanged(position);

                Callback<PostResponse> cb = new Callback<PostResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PostResponse> call,
                                           @NonNull Response<PostResponse> response) {
                        if (!response.isSuccessful()) {
                            post.setBookmarked(isCurrentlyBookmarked);
                            postAdapter.notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PostResponse> call, @NonNull Throwable t) {
                        post.setBookmarked(isCurrentlyBookmarked);
                        postAdapter.notifyItemChanged(position);
                    }
                };

                if (isCurrentlyBookmarked) {
                    apiService.unbookmarkPost(post.getId()).enqueue(cb);
                } else {
                    apiService.bookmarkPost(post.getId()).enqueue(cb);
                }
            }
        });
    }

    private void setupActions() {
        btnPostComment.setOnClickListener(v -> postComment());
    }

    private void loadCurrentUser() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call,
                                   @NonNull Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    ivCurrentUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
                    return;
                }

                String avatarUrl = response.body().getAvatarUrl();
                Glide.with(CommentActivity.this)
                        .load(normalizeUrl(avatarUrl))
                        .circleCrop()
                        .placeholder(R.drawable.profile_placeholder_bg)
                        .error(R.drawable.profile_placeholder_bg)
                        .into(ivCurrentUserAvatar);
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                ivCurrentUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
            }
        });
    }

    private void loadPostDetail() {
        showLoading();
        apiService.getPost(postId).enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostResponse> call,
                                   @NonNull Response<PostResponse> response) {
                hideLoading();
                if (response.isSuccessful() && response.body() != null) {
                    bindPost(response.body());
                    loadComments();
                } else if (response.code() == 403) {
                    if (tryRedirectToFallbackProfile()) {
                        return;
                    }
                    Toast.makeText(CommentActivity.this, "Post not found", Toast.LENGTH_SHORT).show();
                    finish();
                } else if (response.code() == 404) {
                    Toast.makeText(CommentActivity.this, "Post not found", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(CommentActivity.this, R.string.comment_load_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PostResponse> call, @NonNull Throwable t) {
                hideLoading();
                Toast.makeText(
                        CommentActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void loadComments() {
        showLoading();
        apiService.getComments(postId).enqueue(new Callback<List<CommentResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<CommentResponse>> call,
                                   @NonNull Response<List<CommentResponse>> response) {
                hideLoading();
                if (response.isSuccessful()) {
                    List<CommentResponse> comments = response.body() != null
                            ? response.body()
                            : Collections.emptyList();
                    commentAdapter.setComments(comments);
                } else if (response.code() == 404) {
                    commentAdapter.setComments(Collections.emptyList());
                } else {
                    Toast.makeText(CommentActivity.this, R.string.comment_load_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CommentResponse>> call, @NonNull Throwable t) {
                hideLoading();
                Toast.makeText(
                        CommentActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void postComment() {
        String content = etComment.getText().toString().trim();
        if (content.isEmpty()) {
            return;
        }

        btnPostComment.setEnabled(false);
        CreateCommentRequest req = new CreateCommentRequest(content, replyTargetId);

        apiService.addComment(postId, req).enqueue(new Callback<CommentResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentResponse> call,
                                   @NonNull Response<CommentResponse> response) {
                btnPostComment.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    etComment.setText("");
                    etComment.setHint(getString(R.string.comment_hint));
                    replyTargetId = null;
                    loadPostDetail();
                    loadComments();
                } else {
                    Toast.makeText(CommentActivity.this, R.string.comment_post_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentResponse> call, @NonNull Throwable t) {
                btnPostComment.setEnabled(true);
                Toast.makeText(
                        CommentActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void appendEmoji(String emoji) {
        String current = etComment.getText() == null ? "" : etComment.getText().toString();
        String spacer = current.isEmpty() || current.endsWith(" ") ? "" : " ";
        etComment.append(spacer + emoji);
        etComment.requestFocus();
        etComment.setSelection(etComment.getText().length());
    }

    private void bindPost(PostResponse response) {
        Post post = mapResponseToPost(response);
        commentAdapter.setPostAuthorUsername(post.getUsername());
        postAdapter.setPosts(Collections.singletonList(post));
    }

    private Post mapResponseToPost(PostResponse response) {
        String authorId = response.getAuthor() != null ? valueOrEmpty(response.getAuthor().getId()) : "";
        String username = response.getAuthor() != null ? valueOrEmpty(response.getAuthor().getUsername()) : "unknown";
        String avatarUrl = response.getAuthor() != null ? normalizeUrl(response.getAuthor().getAvatarUrl()) : "";
        String imageUrl = "";
        if (response.getMedia() != null && !response.getMedia().isEmpty() && response.getMedia().get(0) != null) {
            imageUrl = normalizeUrl(response.getMedia().get(0).getUrl());
        }

        int likes = response.getStats() != null ? response.getStats().getLikes() : 0;
        int comments = response.getStats() != null ? response.getStats().getComments() : 0;
        int shares = response.getStats() != null ? response.getStats().getShares() : 0;

        Post uiPost = new Post(
                valueOrEmpty(response.getId()),
                authorId,
                username,
                valueOrEmpty(response.getCaption()),
                valueOrEmpty(response.getCreatedAt()),
                likes,
                comments,
                shares,
                avatarUrl,
                imageUrl,
                response.getLocationName(),
                response.getMusicDisplayText(),
                response.hasMusicSuggestion(),
                response.isLiked()
        );

        if (response.getTaggedUsers() != null) {
            uiPost.setTaggedUsers(response.getTaggedUsers());
        }

        return uiPost;
    }

    private void togglePostLike(Post post, int position) {
        boolean isCurrentlyLiked = post.isLiked();
        int currentLikes = post.getLikesCount();

        post.setLiked(!isCurrentlyLiked);
        post.setLikesCount(isCurrentlyLiked ? currentLikes - 1 : currentLikes + 1);
        postAdapter.notifyItemChanged(position);

        Callback<PostResponse> callback = new Callback<PostResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostResponse> call,
                                   @NonNull Response<PostResponse> response) {
                if (!response.isSuccessful()) {
                    post.setLiked(isCurrentlyLiked);
                    post.setLikesCount(currentLikes);
                    postAdapter.notifyItemChanged(position);
                    Toast.makeText(CommentActivity.this, "Failed to update like status", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PostResponse> call, @NonNull Throwable t) {
                post.setLiked(isCurrentlyLiked);
                post.setLikesCount(currentLikes);
                postAdapter.notifyItemChanged(position);
                Toast.makeText(CommentActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        };

        if (isCurrentlyLiked) {
            apiService.unlikePost(post.getId()).enqueue(callback);
        } else {
            apiService.likePost(post.getId()).enqueue(callback);
        }
    }

    private void showLoading() {
        loadingRequests++;
        progressBar.setVisibility(android.view.View.VISIBLE);
    }

    private void hideLoading() {
        loadingRequests = Math.max(loadingRequests - 1, 0);
        progressBar.setVisibility(loadingRequests > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }

        Uri uri = Uri.parse(rawUrl);
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean tryRedirectToFallbackProfile() {
        if (!ENTRY_TYPE_TAG.equals(entryType)) {
            return false;
        }

        if (fallbackUserId == null || fallbackUserId.trim().isEmpty()) {
            return false;
        }

        Toast.makeText(this, "Only followers can see this post", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("targetUserId", fallbackUserId);
        startActivity(intent);
        finish();
        return true;
    }

    @Override
    public void onReplyClicked(CommentResponse comment) {
        replyTargetId = comment.getId();
        String targetUsername = comment.getAuthor() != null ? comment.getAuthor().getUsername() : "unknown";
        String replyHint = "Replying to @" + targetUsername;
        etComment.setHint(replyHint);
        etComment.setText("@" + targetUsername + " ");
        etComment.requestFocus();
        etComment.setSelection(etComment.getText().length());
    }

    @Override
    public void onLikeClicked(CommentResponse comment, int position) {
        boolean isCurrentlyLiked = comment.isLiked();
        int currentLikes = comment.getLikesCount();
        
        // Optimistic UI update
        comment.setLiked(!isCurrentlyLiked);
        comment.setLikesCount(isCurrentlyLiked ? currentLikes - 1 : currentLikes + 1);
        int contentPosition = postAdapter.getItemCount() + position;
        contentAdapter.notifyItemChanged(contentPosition);

        Call<Void> call = isCurrentlyLiked ? 
                apiService.unlikeComment(postId, comment.getId()) : 
                apiService.likeComment(postId, comment.getId());

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    // Revert if failed
                    comment.setLiked(isCurrentlyLiked);
                    comment.setLikesCount(currentLikes);
                    contentAdapter.notifyItemChanged(contentPosition);
                    Toast.makeText(CommentActivity.this, "Failed to update like status", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Revert if failed
                comment.setLiked(isCurrentlyLiked);
                comment.setLikesCount(currentLikes);
                contentAdapter.notifyItemChanged(contentPosition);
                Toast.makeText(CommentActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
