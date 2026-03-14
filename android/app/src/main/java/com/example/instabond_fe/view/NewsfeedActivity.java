package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityMainBinding;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsfeedActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PostAdapter adapter;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);

        adapter = new PostAdapter(new ArrayList<>());
        binding.rvFeed.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeed.setAdapter(adapter);

        binding.bottomNav.setSelectedItemId(R.id.nav_home);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            if (id == R.id.nav_create) {
                startActivity(new Intent(this, CreatePostActivity.class));
                return true;
            }
            return true;
        });

        loadFeed();
    }

    private void loadFeed() {
        apiService.getFeed().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(NewsfeedActivity.this,
                            "Không tải được Newsfeed", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<PostResponse> apiPosts = ApiListParser.parsePostList(gson, response.body());
                adapter.setPosts(mapToUiPosts(apiPosts));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                Toast.makeText(NewsfeedActivity.this,
                        "Lỗi kết nối: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<Post> mapToUiPosts(List<PostResponse> apiPosts) {
        List<Post> result = new ArrayList<>();
        for (PostResponse postResponse : apiPosts) {
            String username = "unknown";
            String avatarUrl = "";
            if (postResponse.getAuthor() != null) {
                if (postResponse.getAuthor().getUsername() != null) {
                    username = postResponse.getAuthor().getUsername();
                }
                if (postResponse.getAuthor().getAvatarUrl() != null) {
                    avatarUrl = postResponse.getAuthor().getAvatarUrl();
                }
            }

            String imageUrl = "";
            if (postResponse.getMedia() != null && !postResponse.getMedia().isEmpty()
                    && postResponse.getMedia().get(0) != null
                    && postResponse.getMedia().get(0).getUrl() != null) {
                imageUrl = postResponse.getMedia().get(0).getUrl();
            }

            int likes = 0;
            int comments = 0;
            if (postResponse.getStats() != null) {
                likes = postResponse.getStats().getLikes();
                comments = postResponse.getStats().getComments();
            }

            result.add(new Post(
                    username,
                    postResponse.getCaption() == null ? "" : postResponse.getCaption(),
                    likes,
                    comments,
                    avatarUrl,
                    imageUrl,
                    postResponse.hasMusicSuggestion()
            ));
        }
        return result;
    }

    private void handleUnauthorized() {
        sessionManager.clearSession();
        Toast.makeText(this, "Phiên đăng nhập đã hết hạn", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
