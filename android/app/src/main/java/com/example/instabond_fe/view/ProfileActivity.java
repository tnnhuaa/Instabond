package com.example.instabond_fe.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityProfileBinding;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();
    private ActivityResultLauncher<String> imagePickerLauncher;
    private String currentUserId;
    private boolean isFollowing = false;

    private ProfileGridAdapter gridAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        gridAdapter = new ProfileGridAdapter();
        binding.rvProfileGrid.setAdapter(gridAdapter);
        gridAdapter.setListener((allPosts, clickedPosition) -> {
            Intent intent = new Intent(this, ProfilePostDetailActivity.class);
            intent.putExtra("targetUserId", currentUserId);
            intent.putExtra("scrollToPosition", clickedPosition);
            startActivity(intent);
        });
        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        // Check for targetUserId in Intent
        String targetUserId = getIntent().getStringExtra("targetUserId");

        // Setup image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                imageUri -> {
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                });

        binding.toolbar.setTitle("");

        if (targetUserId == null || targetUserId.equals(sessionManager.getUserId())) {
            // Viewing my own profile
            binding.btnSettings.setOnClickListener(v -> openSettings());
            binding.btnEditAvatar.setOnClickListener(v -> pickImage());
            binding.btnEditAvatar.setVisibility(View.VISIBLE);
            binding.btnSettings.setVisibility(View.VISIBLE);
            binding.btnSettings.setImageResource(R.drawable.ic_settings);
            binding.bottomNav.bind(this, InstaBottomNavView.Tab.PROFILE);
            loadMyProfile();
        } else {

            binding.btnEditAvatar.setVisibility(View.GONE);

            binding.btnSettings.setVisibility(View.GONE);

            binding.bottomNav.setVisibility(View.GONE);

            setSupportActionBar(binding.toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            binding.toolbar.setNavigationOnClickListener(v -> finish());

            loadUserProfile(targetUserId);
        }

        binding.tabGrid.setOnClickListener(v -> Toast.makeText(this, "Lưới ảnh", Toast.LENGTH_SHORT).show());
        binding.tabTagged.setOnClickListener(v -> Toast.makeText(this, "Có mặt tôi", Toast.LENGTH_SHORT).show());
    }

    private void toggleFollow(String targetUserId) {
        if (isFollowing) {
            apiService.unfollowUser(targetUserId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        isFollowing = false;
                        updateFollowButtonUI(false);
                        Toast.makeText(ProfileActivity.this, "Đã bỏ theo dõi", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                }
            });
        } else {
            apiService.followUser(targetUserId).enqueue(new Callback<FollowUserResponse>() {
                @Override
                public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                    if (response.isSuccessful()) {
                        isFollowing = true;
                        updateFollowButtonUI(true);
                        Toast.makeText(ProfileActivity.this, "Đã theo dõi", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                }
            });
        }
    }

    private void updateFollowButtonUI(boolean following) {
        binding.btnSettings.setVisibility(View.VISIBLE);
        if (following) {
            binding.btnSettings.setImageResource(R.drawable.ic_follow_minus);
            binding.btnSettings.setContentDescription("Unfollow");
        } else {
            binding.btnSettings.setImageResource(R.drawable.ic_follow_plus);
            binding.btnSettings.setContentDescription("Follow");
        }
    }

    private void loadMyProfile() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                handleProfileResponse(response);
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserProfile(String userId) {
        apiService.getUserProfile(userId).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                handleProfileResponse(response);
                checkFollowStatus(userId);
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkFollowStatus(String targetUserId) {
        String myId = sessionManager.getUserId();
        if (myId == null)
            return;

        apiService.getFollowing(myId).enqueue(new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    isFollowing = false;
                    for (FollowUserResponse user : response.body()) {
                        if (targetUserId.equals(user.getId())) {
                            isFollowing = true;
                            break;
                        }
                    }
                    updateFollowButtonUI(isFollowing);
                    binding.btnSettings.setOnClickListener(v -> toggleFollow(targetUserId));
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
            }
        });
    }

    private void handleProfileResponse(Response<UserProfileResponse> response) {
        if (response.code() == 401) {
            handleUnauthorized();
            return;
        }

        if (!response.isSuccessful() || response.body() == null) {
            Toast.makeText(ProfileActivity.this, "Không tải được hồ sơ", Toast.LENGTH_SHORT).show();
            return;
        }

        UserProfileResponse profile = response.body();
        currentUserId = profile.getId();
        bindProfile(profile);
        if (profile.getId() != null && !profile.getId().isEmpty()) {
            loadUserPosts(profile.getId());
        }
    }

    private void bindProfile(UserProfileResponse profile) {
        if (profile.getId() != null && !profile.getId().trim().isEmpty()) {
            currentUserId = profile.getId();
        }

        binding.tvFullname.setText(nonEmpty(profile.getFullName(), profile.getUsername(), "Unknown User"));

        String username = profile.getUsername() != null ? "@" + profile.getUsername() : "";
        binding.tvUsername.setText(username);

        binding.tvBio.setText(nonEmpty(profile.getBio(), "      "));

        binding.tvPostsCount.setText(String.valueOf(profile.getPostsCount()));
        binding.tvFriendsCount.setText(String.valueOf(profile.getFollowersCount()));

        binding.tvFriendsCount.setOnClickListener(v -> {
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_MODE, "followers");
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, profile.getId());
            startActivity(intent);
        });

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
                gridAdapter.setPosts(posts);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
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

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        if (currentUserId != null && !currentUserId.trim().isEmpty()) {
            intent.putExtra("user_id", currentUserId);
        }
        startActivity(intent);
    }

    private void pickImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void uploadAvatar(Uri imageUri) {
        String userId = resolveCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy user id, hãy thử tải lại hồ sơ", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File file = createTempFileFromUri(imageUri);
            if (file == null || !file.exists()) {
                Toast.makeText(this, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create RequestBody for multipart upload
            String mimeType = getContentResolver().getType(imageUri);
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "image/*";
            }

            RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);

            // Show loading state
            binding.btnEditAvatar.setEnabled(false);
            Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

            // Upload avatar
            apiService.uploadAvatar(userId, filePart).enqueue(new Callback<UserProfileResponse>() {
                @Override
                public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                    binding.btnEditAvatar.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(ProfileActivity.this, "Cập nhật ảnh đại diện thành công", Toast.LENGTH_SHORT)
                                .show();
                        // Reload /me to ensure latest avatar_url is shown from server state.
                        loadMyProfile();
                    } else {
                        Toast.makeText(ProfileActivity.this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                    binding.btnEditAvatar.setEnabled(true);
                    Toast.makeText(ProfileActivity.this,
                            "Lỗi tải lên: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            binding.btnEditAvatar.setEnabled(true);
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File createTempFileFromUri(Uri uri) throws IOException {
        String fileName = queryDisplayName(uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "avatar_upload.jpg";
        }

        File tempFile = new File(getCacheDir(), fileName);
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
                OutputStream outputStream = new FileOutputStream(tempFile, false)) {
            if (inputStream == null) {
                return null;
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }

        return tempFile;
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                return cursor.getString(nameIndex);
            }
            return null;
        }
    }

    private String resolveCurrentUserId() {
        String sessionUserId = sessionManager.getUserId();
        if (sessionUserId != null && !sessionUserId.trim().isEmpty()) {
            return sessionUserId;
        }
        if (currentUserId != null && !currentUserId.trim().isEmpty()) {
            return currentUserId;
        }
        return null;
    }
}
