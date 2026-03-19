package com.example.instabond_fe.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        // Setup image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                imageUri -> {
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                }
        );

        binding.toolbar.setTitle("");

        binding.btnSettings.setOnClickListener(v -> openSettings());

        // Camera button to edit avatar
        binding.btnEditAvatar.setOnClickListener(v -> pickImage());

        // Camera button to edit avatar
        binding.btnEditAvatar.setOnClickListener(v -> pickImage());

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.PROFILE);

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
                currentUserId = profile.getId();
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
        if (profile.getId() != null && !profile.getId().trim().isEmpty()) {
            currentUserId = profile.getId();
        }

        // Display full name (or username if full name is missing)
        binding.tvFullname.setText(nonEmpty(profile.getFullName(), profile.getUsername(), "Unknown User"));
        
        // Display username with @ prefix (smaller text below full name)
        String username = profile.getUsername() != null ? "@" + profile.getUsername() : "";
        binding.tvUsername.setText(username);
        
        // Display bio (or default message if empty)
        binding.tvBio.setText(nonEmpty(profile.getBio(), "      "));
        
        // Display stats
        binding.tvPostsCount.setText(String.valueOf(profile.getPostsCount()));
        binding.tvFriendsCount.setText(String.valueOf(profile.getFollowersCount()));

        // Load avatar image
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
                        Toast.makeText(ProfileActivity.this, "Cập nhật ảnh đại diện thành công", Toast.LENGTH_SHORT).show();
                        // Reload /me to ensure latest avatar_url is shown from server state.
                        loadProfile();
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
