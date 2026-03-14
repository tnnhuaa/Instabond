package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityProfileBinding;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        binding.toolbar.setTitle("");

        binding.btnSettings.setOnClickListener(v -> logoutForTesting());

        binding.navHome.setOnClickListener(v -> {
            startActivity(new Intent(this, NewsfeedActivity.class));
            finish();
        });

        binding.btnCreate.setOnClickListener(v ->
                Toast.makeText(this, "Tạo bài viết (mock)", Toast.LENGTH_SHORT).show());

        binding.tabGrid.setOnClickListener(v ->
                Toast.makeText(this, "Lưới ảnh", Toast.LENGTH_SHORT).show());
        binding.tabTagged.setOnClickListener(v ->
                Toast.makeText(this, "Có mặt tôi", Toast.LENGTH_SHORT).show());

        loadProfile();
    }

    private void loadProfile() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(ProfileActivity.this, "Không tải được hồ sơ", Toast.LENGTH_SHORT).show();
                    return;
                }

                UserProfileResponse profile = response.body();
                bindProfile(profile);
                if (profile.getId() != null && !profile.getId().isEmpty()) {
                    loadUserPosts(profile.getId());
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this,
                        "Lỗi kết nối: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindProfile(UserProfileResponse profile) {
        binding.tvUsername.setText(nonEmpty(profile.getFullName(), profile.getUsername(), "Unknown user"));
        binding.tvBio.setText(nonEmpty(profile.getBio(), "Chưa có tiểu sử"));
        binding.tvPostsCount.setText(String.valueOf(profile.getPostsCount()));
        binding.tvFriendsCount.setText(String.valueOf(profile.getFollowersCount()));

        Glide.with(this)
                .load(profile.getAvatarUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(binding.ivAvatar);
    }

    private void loadUserPosts(String userId) {
        apiService.getPostsByUserId(userId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                List<PostResponse> posts = ApiListParser.parsePostList(gson, response.body());
                bindLikesCount(posts);
                bindPhotoGrid(extractPhotoUrls(posts));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                // Keep profile visible even if posts fail.
            }
        });
    }

    private void bindLikesCount(List<PostResponse> posts) {
        int totalLikes = 0;
        for (PostResponse post : posts) {
            if (post.getStats() != null) {
                totalLikes += post.getStats().getLikes();
            }
        }
        binding.tvLikesCount.setText(String.valueOf(totalLikes));
    }

    private List<String> extractPhotoUrls(List<PostResponse> posts) {
        List<String> urls = new ArrayList<>();
        for (PostResponse post : posts) {
            if (post.getMedia() == null || post.getMedia().isEmpty()) {
                continue;
            }
            for (PostResponse.MediaItem mediaItem : post.getMedia()) {
                if (mediaItem != null && mediaItem.getUrl() != null && !mediaItem.getUrl().isEmpty()) {
                    urls.add(mediaItem.getUrl());
                    if (urls.size() == 6) {
                        return urls;
                    }
                }
            }
        }
        return urls;
    }

    private void bindPhotoGrid(List<String> photoUrls) {
        List<ImageView> cells = Arrays.asList(
                binding.ivPhoto1,
                binding.ivPhoto2,
                binding.ivPhoto3,
                binding.ivPhoto4,
                binding.ivPhoto5,
                binding.ivPhoto6
        );

        for (int i = 0; i < cells.size(); i++) {
            String url = i < photoUrls.size() ? photoUrls.get(i) : null;
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.avatar_circle_bg)
                    .error(R.drawable.avatar_circle_bg)
                    .into(cells.get(i));
        }
    }

    private String nonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private void handleUnauthorized() {
        sessionManager.clearSession();
        Toast.makeText(this, "Phiên đăng nhập đã hết hạn", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void logoutForTesting() {
        sessionManager.clearSession();
        Toast.makeText(this, "Đã đăng xuất (test)", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
