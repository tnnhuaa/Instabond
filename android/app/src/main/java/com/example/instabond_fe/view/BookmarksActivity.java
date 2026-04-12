package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.utils.LocaleManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BookmarksActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private PostAdapter adapter;
    private ApiService apiService;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bookmarks");
        }
        toolbar.setNavigationContentDescription(getString(R.string.cd_back));
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new PostAdapter(new ArrayList<>());
        RecyclerView rvBookmarks = findViewById(R.id.rv_bookmarks);
        rvBookmarks.setLayoutManager(new LinearLayoutManager(this));
        rvBookmarks.setAdapter(adapter);

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
                            post.setLiked(isCurrentlyLiked);
                            post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? 1 : -1));
                            adapter.notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {
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
                Intent intent = new Intent(BookmarksActivity.this, CommentActivity.class);
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

                com.example.instabond_fe.utils.ShareUtils.showShareBottomSheet(
                    BookmarksActivity.this, post, apiService,
                    new com.example.instabond_fe.network.SessionManager(BookmarksActivity.this).getUserId()
                );
            }

            @Override
            public void onUserClicked(Post post, int position) {
                Intent intent = new Intent(BookmarksActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", post.getAuthorId());
                startActivity(intent);
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
        });

        loadBookmarkedPosts();
    }

    private void loadBookmarkedPosts() {
        apiService.getBookmarkedPosts().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PostResponse> postResponses = ApiListParser.parsePostList(gson, response.body());
                    List<Post> posts = mapResponseToModel(postResponses);
                    adapter.setPosts(posts);
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                Toast.makeText(BookmarksActivity.this, "Lỗi tải bookmarks", Toast.LENGTH_SHORT).show();
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

            boolean hasMusic = r.hasMusicSuggestion();
            boolean isLiked = r.isLiked();

            Post p = new Post(
                    id, authorId, username, caption,
                    r.getCreatedAt(),
                    likes, comments, shares,
                    avatar, image,
                    r.getLocationName(),
                    r.getMusicDisplayText(),
                    hasMusic, isLiked, true
            );

            list.add(p);
        }
        return list;
    }
}
