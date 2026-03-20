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
            boolean isLiked = false;

            Post p = new Post(
                    id, authorId, username, caption,
                    likes, comments, shares,
                    avatar, image, hasMusic, isLiked
            );

            list.add(p);
        }
        return list;
    }
}