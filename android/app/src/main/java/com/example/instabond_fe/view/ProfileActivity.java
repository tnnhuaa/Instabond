package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
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
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private static final String HIGHLIGHT_PROCESS =
            "https://www.figma.com/api/mcp/asset/5b99f577-4b4d-478f-9713-de1a77a92f1b";
    private static final String HIGHLIGHT_VIBE =
            "https://www.figma.com/api/mcp/asset/37d6dab0-ce83-4973-853c-c638eac998cd";
    private static final String HIGHLIGHT_TRAVEL =
            "https://www.figma.com/api/mcp/asset/9220028b-16cc-45b0-a2b0-df2799b074db";
    private static final String HIGHLIGHT_NATURE =
            "https://www.figma.com/api/mcp/asset/369303e1-7b73-438e-be7b-9c65d568a10b";

    private ActivityProfileBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();
    private ActivityResultLauncher<String> imagePickerLauncher;

    private String currentUserId;
    private boolean isFollowing;
    private boolean isOwnProfileView;
    private ProfileGridAdapter gridAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        gridAdapter = new ProfileGridAdapter();
        binding.rvProfileGrid.setAdapter(gridAdapter);
        gridAdapter.setListener((allPosts, clickedPosition) -> {
            Intent intent = new Intent(this, ProfilePostDetailActivity.class);
            intent.putExtra("targetUserId", currentUserId);
            intent.putExtra("scrollToPosition", clickedPosition);
            startActivity(intent);
        });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                imageUri -> {
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                });

        bindHighlightImages();

        String targetUserId = getIntent().getStringExtra("targetUserId");
        isOwnProfileView = targetUserId == null || targetUserId.equals(sessionManager.getUserId());

        if (isOwnProfileView) {
            configureOwnProfileView();
            loadMyProfile();
        } else {
            configureExternalProfileView(targetUserId);
            loadUserProfile(targetUserId);
        }
    }

    private void configureOwnProfileView() {
        binding.bottomNav.bind(this, InstaBottomNavView.Tab.PROFILE);
        binding.bottomNav.setVisibility(View.VISIBLE);
        binding.btnEditAvatar.setVisibility(View.VISIBLE);
        binding.btnEditAvatar.setOnClickListener(v -> pickImage());

        binding.btnSettings.setVisibility(View.VISIBLE);
        binding.btnSettings.setImageResource(R.drawable.ic_settings);
        binding.btnSettings.setContentDescription(getString(R.string.cd_settings));
        binding.btnSettings.setOnClickListener(v -> openSettings());

        binding.btnPrimaryAction.setText(R.string.profile_action_edit);
        binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_button_bg);
        binding.btnPrimaryAction.setTextColor(getColor(R.color.login_primary_text));
        binding.btnPrimaryAction.setOnClickListener(v -> openSettings());

        binding.btnSecondaryAction.setText(R.string.profile_action_share);
        binding.btnSecondaryAction.setBackgroundResource(R.drawable.search_follow_back_button_bg);
        binding.btnSecondaryAction.setTextColor(getColor(R.color.login_text_primary));
        binding.btnSecondaryAction.setOnClickListener(v -> shareProfile());
    }

    private void configureExternalProfileView(String targetUserId) {
        binding.bottomNav.setVisibility(View.GONE);
        binding.btnEditAvatar.setVisibility(View.GONE);

        binding.btnSettings.setVisibility(View.VISIBLE);
        binding.btnSettings.setImageResource(R.drawable.ic_arrow_back);
        binding.btnSettings.setContentDescription(getString(R.string.cd_back));
        binding.btnSettings.setOnClickListener(v -> finish());

        binding.btnPrimaryAction.setText(R.string.profile_action_follow);
        binding.btnPrimaryAction.setOnClickListener(v -> toggleFollow(targetUserId));

        binding.btnSecondaryAction.setText(R.string.profile_action_message);
        binding.btnSecondaryAction.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
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
        if (following) {
            binding.btnPrimaryAction.setText(R.string.profile_action_following);
            binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_back_button_bg);
            binding.btnPrimaryAction.setTextColor(getColor(R.color.login_text_primary));
        } else {
            binding.btnPrimaryAction.setText(R.string.profile_action_follow);
            binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_button_bg);
            binding.btnPrimaryAction.setTextColor(getColor(R.color.login_primary_text));
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
        if (myId == null) {
            return;
        }

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

        String displayName = nonEmpty(profile.getUsername(), profile.getFullName(), "Unknown User");
        String subtitle = nonEmpty(profile.getFullName(), "Digital Artist & Storyteller");
        String bio = nonEmpty(profile.getBio(),
                "Capturing the ethereal in the everyday. Currently exploring the intersection of AI and human emotion.");
        String link = profile.getUsername() == null || profile.getUsername().trim().isEmpty()
                ? ""
                : "linktr.ee/" + profile.getUsername().replace("@", "");

        binding.tvFullname.setText(displayName);
        binding.tvUsername.setText(subtitle);
        binding.tvBio.setText(bio);
        binding.tvProfileLink.setText(link);
        binding.tvProfileLink.setVisibility(link.isEmpty() ? View.GONE : View.VISIBLE);

        binding.tvPostsCount.setText(formatCount(profile.getPostsCount()));
        binding.tvFriendsCount.setText(formatCount(profile.getFollowersCount()));
        binding.tvLikesCount.setText(formatCount(profile.getFollowingCount()));

        binding.tvFriendsCount.setOnClickListener(v -> openFollowList(profile.getId(), "followers"));
        binding.tvLikesCount.setOnClickListener(v -> openFollowList(profile.getId(), "following"));

        Glide.with(this)
                .load(profile.getAvatarUrl())
                .placeholder(R.drawable.profile_placeholder_bg)
                .error(R.drawable.profile_placeholder_bg)
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
                gridAdapter.setPosts(posts);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
            }
        });
    }

    private void bindHighlightImages() {
        loadImage(binding.ivHighlightProcess, HIGHLIGHT_PROCESS);
        loadImage(binding.ivHighlightVibe, HIGHLIGHT_VIBE);
        loadImage(binding.ivHighlightTravel, HIGHLIGHT_TRAVEL);
        loadImage(binding.ivHighlightNature, HIGHLIGHT_NATURE);
    }

    private void loadImage(ImageView view, String url) {
        Glide.with(this)
                .load(url)
                .centerCrop()
                .into(view);
    }

    private void openFollowList(String userId, String mode) {
        Intent intent = new Intent(this, FollowListActivity.class);
        intent.putExtra(FollowListActivity.EXTRA_MODE, mode);
        intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
        startActivity(intent);
    }

    private String formatCount(int value) {
        if (value >= 1000000) {
            return String.format(java.util.Locale.US, "%.1fm", value / 1000000f).replace(".0", "");
        }
        if (value >= 1000) {
            return String.format(java.util.Locale.US, "%.1fk", value / 1000f).replace(".0", "");
        }
        return String.valueOf(value);
    }

    private String nonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private void shareProfile() {
        String username = binding.tvFullname.getText().toString().trim();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out " + username + " on Instabond");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.profile_action_share)));
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

            String mimeType = getContentResolver().getType(imageUri);
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "image/*";
            }

            RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);

            binding.btnEditAvatar.setEnabled(false);
            Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

            apiService.uploadAvatar(userId, filePart).enqueue(new Callback<UserProfileResponse>() {
                @Override
                public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                    binding.btnEditAvatar.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(ProfileActivity.this, "Cập nhật ảnh đại diện thành công", Toast.LENGTH_SHORT)
                                .show();
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
