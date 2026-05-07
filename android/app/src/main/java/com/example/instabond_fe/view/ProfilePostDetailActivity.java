package com.example.instabond_fe.view;

import android.os.Bundle;
import android.widget.Toast;
import android.widget.PopupMenu;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.instabond_fe.databinding.ActivityProfilePostDetailBinding;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.DeletedPostRegistry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.annotation.NonNull;
import com.example.instabond_fe.R;
import com.example.instabond_fe.utils.LocaleManager;
public class ProfilePostDetailActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private ActivityProfilePostDetailBinding binding;
    private PostAdapter adapter;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();
    private int lastHandledDeletedPostVersion = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfilePostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new PostAdapter(new ArrayList<>());
        adapter.setShowOverflowActions(true);
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPosts.setAdapter(adapter);

        String userId = getIntent().getStringExtra("targetUserId");
        int startPosition = getIntent().getIntExtra("scrollToPosition", 0);

        if (userId != null) {
            loadPosts(userId, startPosition);
        }
        adapter.setListener(new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(Post post, int position) {
                boolean isCurrentlyLiked = post.isLiked();
                post.setLiked(!isCurrentlyLiked);
                post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? -1 : 1));
                adapter.notifyItemChanged(position);

                Callback<PostResponse> cb = new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (!response.isSuccessful()) {
                            // Revert on failure
                            post.setLiked(isCurrentlyLiked);
                            post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? 1 : -1));
                            adapter.notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {
                        // Revert on failure
                        post.setLiked(isCurrentlyLiked);
                        post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? 1 : -1));
                        adapter.notifyItemChanged(position);
                    }
                };

                if (isCurrentlyLiked) {
                    apiService.unlikePost(post.getId()).enqueue(cb);
                } else {
                    apiService.likePost(post.getId()).enqueue(cb);
                }
            }

            @Override
            public void onCommentClicked(Post post, int position) {
                android.content.Intent intent = new android.content.Intent(ProfilePostDetailActivity.this, CommentActivity.class);
                intent.putExtra("postId", post.getId());
                intent.putExtra("postUsername", post.getUsername());
                intent.putExtra("postCaption", post.getCaption());
                intent.putExtra("postAvatarUrl", post.getAvatarUrl());
                intent.putExtra("postCreatedAt", post.getCreatedAt());
                startActivity(intent);
            }
            @Override
            public void onShareClicked(Post post, int position) {
                apiService.sharePost(post.getId()).enqueue(new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (response.isSuccessful()) {
                            post.setSharesCount(post.getSharesCount() + 1);
                            adapter.notifyItemChanged(position);
                        }
                    }
                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {}
                });

                com.example.instabond_fe.network.SessionManager sessionManager = new com.example.instabond_fe.network.SessionManager(ProfilePostDetailActivity.this);
                com.example.instabond_fe.utils.ShareUtils.showShareBottomSheet(ProfilePostDetailActivity.this, post, apiService, sessionManager.getUserId());
            }
            @Override
            public void onUserClicked(Post post, int position) {
                finish();
            }

            @Override
            public void onBookmarkClicked(Post post, int position) {
                boolean isCurrentlyBookmarked = post.isBookmarked();
                post.setBookmarked(!isCurrentlyBookmarked);
                adapter.notifyItemChanged(position);

                Callback<PostResponse> cb = new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (!response.isSuccessful()) {
                            post.setBookmarked(isCurrentlyBookmarked);
                            adapter.notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {
                        post.setBookmarked(isCurrentlyBookmarked);
                        adapter.notifyItemChanged(position);
                    }
                };

                if (isCurrentlyBookmarked) {
                    apiService.unbookmarkPost(post.getId()).enqueue(cb);
                } else {
                    apiService.bookmarkPost(post.getId()).enqueue(cb);
                }
            }

            @Override
            public void onOptionsClicked(Post post, int position, android.view.View anchorView) {
                showPostOptionsMenu(post, position, anchorView);
            }
        });
    }

    @Override
    protected void onPause() {
        PostAdapter.stopAudioPlayback();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncDeletedPosts();
    }

    private void loadPosts(String userId, int startPosition) {
        apiService.getPostsByUserId(userId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PostResponse> postResponses = ApiListParser.parsePostList(gson, response.body());
                    List<Post> postsForAdapter = mapResponseToModel(postResponses);

                    adapter.setPosts(postsForAdapter);


                    binding.rvPosts.scrollToPosition(startPosition);
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                Toast.makeText(ProfilePostDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<Post> mapResponseToModel(List<PostResponse> responses) {
        List<Post> list = new ArrayList<>();
        for (PostResponse r : responses) {
            String id = r.getId();
            String authorId = (r.getAuthor() != null) ? r.getAuthor().getId() : "";
            String username = (r.getAuthor() != null) ? r.getAuthor().getUsername() : "unknown";
            String caption = r.getCaption();

            int likes = (r.getStats() != null) ? r.getStats().getLikes() : 0;
            int comments = (r.getStats() != null) ? r.getStats().getComments() : 0;
            int shares = (r.getStats() != null) ? r.getStats().getShares() : 0;

            String avatar = (r.getAuthor() != null) ? r.getAuthor().getAvatarUrl() : "";

            String image = "";
            if (r.getMedia() != null && !r.getMedia().isEmpty()) {
                image = r.getMedia().get(0).getUrl();
            }

            String musicPreviewUrl = r.getMusicPreviewUrl();
            boolean hasMusic = !musicPreviewUrl.isEmpty();
            boolean isLiked = r.isLiked();

            Post p = new Post(
                    id, authorId, username, caption,
                    r.getCreatedAt(),
                    likes, comments, shares,
                    avatar, image,
                    r.getLocationName(),
                    r.getMusicDisplayText(),
                    musicPreviewUrl,
                    hasMusic, isLiked, r.isBookmarked()
            );
            p.setOwnedByCurrentUser(authorId.equals(valueOrEmpty(sessionManager.getUserId())));

            list.add(p);
        }
        return list;
    }

    private void showPostOptionsMenu(Post post, int position, android.view.View anchorView) {
        if (post == null || !post.isOwnedByCurrentUser()) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add(0, 1, 0, R.string.post_delete_action);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showDeletePostConfirmation(post, position);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showDeletePostConfirmation(Post post, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.post_delete_title)
                .setMessage(R.string.post_delete_message)
                .setPositiveButton(R.string.post_delete_confirm, (dialog, which) -> deletePost(post, position))
                .setNegativeButton(R.string.post_delete_cancel, null)
                .show();
    }

    private void deletePost(Post post, int position) {
        apiService.deletePost(post.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    DeletedPostRegistry.markDeleted(post.getId());
                    adapter.removePostAt(position);
                    Toast.makeText(ProfilePostDetailActivity.this, R.string.post_delete_success, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK, new android.content.Intent().putExtra("deletedPostId", post.getId()));
                    if (adapter.getItemCount() == 0) {
                        finish();
                    }
                } else {
                    Toast.makeText(ProfilePostDetailActivity.this, R.string.post_delete_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(
                        ProfilePostDetailActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void syncDeletedPosts() {
        int currentVersion = DeletedPostRegistry.getVersion();
        if (currentVersion == lastHandledDeletedPostVersion) {
            return;
        }

        lastHandledDeletedPostVersion = currentVersion;
        boolean removed = adapter.removePostsByIds(DeletedPostRegistry.snapshotDeletedPostIds());
        if (removed && adapter.getItemCount() == 0) {
            finish();
        }
    }
}
