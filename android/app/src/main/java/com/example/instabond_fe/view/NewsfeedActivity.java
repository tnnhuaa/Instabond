package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityMainBinding;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsfeedActivity extends AppCompatActivity {
    private static final String EXTRA_REFRESH_FEED = "refresh_feed";
    private static final int PAGE_SIZE = 5;
    private static final int VISIBLE_THRESHOLD = 2;

    private ActivityMainBinding binding;
    private PostAdapter adapter;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();

    private final Set<String> loadedPostIds = new HashSet<>();
    private int currentPage;
    private boolean isRequestInFlight;
    private boolean reachedEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        adapter = new PostAdapter(new ArrayList<>());
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
                Intent intent = new Intent(NewsfeedActivity.this, CommentActivity.class);
                intent.putExtra("postId", post.getId());
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
                
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Xem bài viết của " + post.getUsername() + " trên InstaBond!");
                startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài viết"));
            }

            @Override
            public void onUserClicked(Post post, int position) {
                Intent intent = new Intent(NewsfeedActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", post.getAuthorId());
                startActivity(intent);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvFeed.setLayoutManager(layoutManager);
        binding.rvFeed.setAdapter(adapter);
        binding.rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || reachedEnd || isRequestInFlight) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (lastVisible >= total - 1 - VISIBLE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        binding.swipeRefreshFeed.setOnRefreshListener(this::refreshFeed);
        binding.swipeRefreshFeed.setColorSchemeResources(R.color.login_bg_start, R.color.login_bg_mid);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.HOME);
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());

        binding.swipeRefreshFeed.setRefreshing(true);
        refreshFeed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_FEED, false)) {
            binding.swipeRefreshFeed.setRefreshing(true);
            refreshFeed();
            intent.removeExtra(EXTRA_REFRESH_FEED);
        }
    }

    private void refreshFeed() {
        if (isRequestInFlight) {
            return;
        }
        currentPage = 0;
        reachedEnd = false;
        loadedPostIds.clear();
        adapter.setPosts(new ArrayList<>());
        loadPage(true);
    }

    private void loadNextPage() {
        if (isRequestInFlight || reachedEnd) {
            return;
        }
        loadPage(false);
    }

    private void loadPage(boolean fromRefresh) {
        isRequestInFlight = true;
        apiService.getFeed(currentPage, PAGE_SIZE).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                isRequestInFlight = false;
                if (fromRefresh) {
                    binding.swipeRefreshFeed.setRefreshing(false);
                }

                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(NewsfeedActivity.this,
                            "Khong tai duoc Newsfeed", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<PostResponse> pagePosts = ApiListParser.parsePostList(gson, response.body());
                List<PostResponse> uniquePosts = filterNewPosts(pagePosts);
                List<Post> mappedPosts = mapToUiPosts(uniquePosts);

                if (fromRefresh) {
                    adapter.setPosts(mappedPosts);
                } else {
                    adapter.appendPosts(mappedPosts);
                }

                if (pagePosts.size() < PAGE_SIZE || uniquePosts.isEmpty()) {
                    reachedEnd = true;
                } else {
                    currentPage++;
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                isRequestInFlight = false;
                if (fromRefresh) {
                    binding.swipeRefreshFeed.setRefreshing(false);
                }
                Toast.makeText(NewsfeedActivity.this,
                        "Loi ket noi: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<PostResponse> filterNewPosts(List<PostResponse> apiPosts) {
        List<PostResponse> result = new ArrayList<>();
        for (PostResponse post : apiPosts) {
            String key = buildPostKey(post);
            if (loadedPostIds.add(key)) {
                result.add(post);
            }
        }
        return result;
    }

    private String buildPostKey(PostResponse post) {
        if (post.getId() != null && !post.getId().trim().isEmpty()) {
            return post.getId();
        }
        String username = post.getAuthor() == null ? "" : String.valueOf(post.getAuthor().getUsername());
        String caption = String.valueOf(post.getCaption());
        String mediaUrl = "";
        if (post.getMedia() != null && !post.getMedia().isEmpty() && post.getMedia().get(0) != null) {
            mediaUrl = String.valueOf(post.getMedia().get(0).getUrl());
        }
        return username + "|" + caption + "|" + mediaUrl;
    }

    private List<Post> mapToUiPosts(List<PostResponse> apiPosts) {
        List<Post> result = new ArrayList<>();
        for (PostResponse postResponse : apiPosts) {
            String username = "unknown";
            String avatarUrl = "";
            String postId = postResponse.getId();
            String authorId = "";

            if (postResponse.getAuthor() != null) {
                if (postResponse.getAuthor().getId() != null) {
                    authorId = postResponse.getAuthor().getId();
                }
                if (postResponse.getAuthor().getUsername() != null) {
                    username = postResponse.getAuthor().getUsername();
                }
                if (postResponse.getAuthor().getAvatarUrl() != null) {
                    avatarUrl = normalizeUrl(postResponse.getAuthor().getAvatarUrl());
                }
            }

            String imageUrl = "";
            if (postResponse.getMedia() != null && !postResponse.getMedia().isEmpty()
                    && postResponse.getMedia().get(0) != null
                    && postResponse.getMedia().get(0).getUrl() != null) {
                imageUrl = normalizeUrl(postResponse.getMedia().get(0).getUrl());
            }

            int likes = 0;
            int comments = 0;
            int shares = 0;
            if (postResponse.getStats() != null) {
                likes = postResponse.getStats().getLikes();
                comments = postResponse.getStats().getComments();
                shares = postResponse.getStats().getShares();
            }

            result.add(new Post(
                    postId,
                    authorId,
                    username,
                    postResponse.getCaption() == null ? "" : postResponse.getCaption(),
                    likes,
                    comments,
                    shares,
                    avatarUrl,
                    imageUrl,
                    postResponse.hasMusicSuggestion(),
                    false // Assuming default false until we get state from server
            ));
        }
        return result;
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
            if (baseUrl.endsWith("/")) {
                return baseUrl.substring(0, baseUrl.length() - 1) + rawUrl;
            }
            return baseUrl + rawUrl;
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl + rawUrl;
        }
        return baseUrl + "/" + rawUrl;
    }

    private void handleUnauthorized() {
        sessionManager.clearSession();
        Toast.makeText(this, "Phien dang nhap da het han", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
