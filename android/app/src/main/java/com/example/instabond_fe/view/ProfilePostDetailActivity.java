package com.example.instabond_fe.view;

import android.os.Bundle;
import android.widget.Toast;
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

public class ProfilePostDetailActivity extends AppCompatActivity {

    private ActivityProfilePostDetailBinding binding;
    private PostAdapter adapter;
    private ApiService apiService;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfilePostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new PostAdapter(new ArrayList<>());
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
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {}
                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {}
                });

                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Xem bài viết của " + post.getUsername() + " trên InstaBond!");
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ bài viết"));
            }

            @Override
            public void onUserClicked(Post post, int position) {
                finish();
            }
        });
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

            boolean hasMusic = r.hasMusicSuggestion();
            boolean isLiked = r.isLiked();

            Post p = new Post(
                    id, authorId, username, caption,
                    r.getCreatedAt(),
                    likes, comments, shares,
                    avatar, image, hasMusic, isLiked
            );

            list.add(p);
        }
        return list;
    }

}
