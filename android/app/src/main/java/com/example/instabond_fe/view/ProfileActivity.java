package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityProfileBinding;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.ProfileShareResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_NAV_CONTEXT = "profile_nav_context";
    public static final String NAV_CONTEXT_SEARCH = "search";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private ActivityProfileBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();
    private ActivityResultLauncher<String> imagePickerLauncher;

    private String currentUserId;
    private String currentAvatarUrl = "";
    private String currentUsername = "";
    private ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> barcodeLauncher;
    private boolean isFollowing;
    private boolean isOwnProfileView;
    private boolean shouldAttemptQuickFollow;
    private String quickFollowTargetUserId;
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
            if (clickedPosition < 0 || clickedPosition >= allPosts.size()) {
                return;
            }

            PostResponse selectedPost = allPosts.get(clickedPosition);
            if (selectedPost == null || selectedPost.getId() == null || selectedPost.getId().trim().isEmpty()) {
                return;
            }

            Intent intent = new Intent(this, CommentActivity.class);
            intent.putExtra("postId", selectedPost.getId());
            startActivity(intent);
        });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                imageUri -> {
                    if (imageUri != null) {
                        uploadAvatar(imageUri);
                    }
                });

        barcodeLauncher = registerForActivityResult(
                new com.journeyapps.barcodescanner.ScanContract(),
                result -> {
                    if (result == null || result.getContents() == null || result.getContents().trim().isEmpty()) {
                        return;
                    }
                    resolveProfileFromPayload(result.getContents().trim(), false);
                });

        handleIntentNavigation(getIntent());
        binding.btnScanQr.setOnClickListener(v -> {
            com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
            options.setPrompt("Quét mã QR");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
            barcodeLauncher.launch(options);
        });

        binding.btnShowQr.setOnClickListener(v -> {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Toast.makeText(this, "Không thể tải thông tin hồ sơ", Toast.LENGTH_SHORT).show();
                return;
            }

            ProfileQrDialogFragment qrDialog = ProfileQrDialogFragment.newInstance(
                    currentUserId,
                    currentUsername,
                    currentAvatarUrl
            );
            qrDialog.show(getSupportFragmentManager(), "ProfileQrDialog");
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentNavigation(intent);
    }

    private void handleIntentNavigation(Intent intent) {
        shouldAttemptQuickFollow = false;
        quickFollowTargetUserId = null;

        if (intent == null) {
            configureOwnProfileView();
            loadMyProfile();
            return;
        }

        Uri data = intent.getData();
        boolean isResolvingDeepLink = false;
        if (data != null) {
            String deepLinkUserId = extractDirectUserId(data);
            if (deepLinkUserId != null) {
                shouldAttemptQuickFollow = true;
                quickFollowTargetUserId = deepLinkUserId;
                intent.putExtra("targetUserId", deepLinkUserId);
            } else if (isProfilePayload(data)) {
                isResolvingDeepLink = true;
                String payload = extractResolvablePayload(data);
                resolveProfileFromPayload(payload, true);
            }
        }

        if (!isResolvingDeepLink) {
            String payloadFromExtras = extractExternalPayloadFromIntent(intent);
            if (payloadFromExtras != null && !payloadFromExtras.trim().isEmpty()) {
                isResolvingDeepLink = true;
                shouldAttemptQuickFollow = true;
                quickFollowTargetUserId = null;
                resolveProfileFromPayload(payloadFromExtras.trim(), true);
            }
        }

        String targetUserId = intent.getStringExtra("targetUserId");
        boolean showSearchBottomNav = NAV_CONTEXT_SEARCH.equals(
                intent.getStringExtra(EXTRA_PROFILE_NAV_CONTEXT)
        );
        isOwnProfileView = targetUserId == null || targetUserId.equals(sessionManager.getUserId());

        if (!isResolvingDeepLink) {
            if (isOwnProfileView) {
                configureOwnProfileView();
                loadMyProfile();
            } else {
                configureExternalProfileView(targetUserId, showSearchBottomNav);
                loadUserProfile(targetUserId);
            }
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

    private void configureExternalProfileView(String targetUserId, boolean showSearchBottomNav) {
        binding.bottomNav.setVisibility(showSearchBottomNav ? View.VISIBLE : View.GONE);
        if (showSearchBottomNav) {
            binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);
        }
        binding.btnEditAvatar.setVisibility(View.GONE);

        binding.btnSettings.setVisibility(View.VISIBLE);
        binding.btnSettings.setImageResource(R.drawable.ic_menu);
        binding.btnSettings.setContentDescription(getString(R.string.cd_more));
        binding.btnSettings.setOnClickListener(v -> showProfileOptionsMenu(targetUserId));

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
                        Toast.makeText(ProfileActivity.this, "Đã hủy yêu cầu/Bỏ theo dõi", Toast.LENGTH_SHORT).show();
                        // Tải lại hồ sơ để cập nhật giao diện
                        loadUserProfile(targetUserId);
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
                        Toast.makeText(ProfileActivity.this, "Đã gửi yêu cầu/Theo dõi", Toast.LENGTH_SHORT).show();
                        // Tải lại hồ sơ để cập nhật giao diện
                        loadUserProfile(targetUserId);
                    }
                }

                @Override
                public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                }
            });
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
                if (response.code() == 404) {
                    Toast.makeText(ProfileActivity.this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                handleProfileResponse(response);
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
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
    }

    private void bindProfile(UserProfileResponse profile) {
        if (profile.getId() != null && !profile.getId().trim().isEmpty()) {
            currentUserId = profile.getId();
        }
        currentUsername = profile.getUsername();
        currentAvatarUrl = profile.getAvatarUrl();

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

        AvatarLoader.load(binding.ivAvatar, profile.getAvatarUrl());

        String relStatus = profile.getRelationshipStatus();
        boolean isPrivate = profile.isPrivate();

        if (!isOwnProfileView) {
            if ("accepted".equals(relStatus)) {
                isFollowing = true;
                binding.btnPrimaryAction.setText("Following");
                binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_back_button_bg);
                binding.btnPrimaryAction.setTextColor(getColor(R.color.login_text_primary));
            } else if ("pending".equals(relStatus)) {
                isFollowing = true;
                binding.btnPrimaryAction.setText("Pending");
                binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_back_button_bg);
                binding.btnPrimaryAction.setTextColor(getColor(R.color.login_text_primary));
            } else {
                isFollowing = false;
                binding.btnPrimaryAction.setText("Follow");
                binding.btnPrimaryAction.setBackgroundResource(R.drawable.search_follow_button_bg);
                binding.btnPrimaryAction.setTextColor(getColor(R.color.login_primary_text));
            }
        }

        boolean canViewDetails = isOwnProfileView || !isPrivate || "accepted".equals(relStatus);

        if (canViewDetails) {
            if (binding.layoutPrivateAccount != null) {
                binding.layoutPrivateAccount.setVisibility(View.GONE);
            }
            binding.rvProfileGrid.setVisibility(View.VISIBLE);

            binding.tvFriendsCount.setOnClickListener(v -> openFollowList(profile.getId(), "followers"));
            binding.tvLikesCount.setOnClickListener(v -> openFollowList(profile.getId(), "following"));

            if (profile.getId() != null && !profile.getId().isEmpty()) {
                loadUserPosts(profile.getId());
            }
        } else {
            if (binding.layoutPrivateAccount != null) {
                binding.layoutPrivateAccount.setVisibility(View.VISIBLE);
            }
            binding.rvProfileGrid.setVisibility(View.GONE);

            gridAdapter.setPosts(java.util.Collections.emptyList());

            binding.tvFriendsCount.setOnClickListener(v -> Toast.makeText(this, "Bạn cần theo dõi để xem danh sách này", Toast.LENGTH_SHORT).show());
            binding.tvLikesCount.setOnClickListener(v -> Toast.makeText(this, "Bạn cần theo dõi để xem danh sách này", Toast.LENGTH_SHORT).show());
        }
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
        apiService.getMyShareProfile().enqueue(new Callback<ProfileShareResponse>() {
            @Override
            public void onResponse(Call<ProfileShareResponse> call, Response<ProfileShareResponse> response) {
                String shareText = null;
                if (response.isSuccessful() && response.body() != null) {
                    shareText = extractShareableProfileLink(response.body());
                    if (shareText == null || shareText.trim().isEmpty()) {
                        String backendShareText = response.body().getShareText();
                        String extractedLink = extractLinkFromText(backendShareText);
                        shareText = (extractedLink == null || extractedLink.trim().isEmpty())
                                ? backendShareText
                                : extractedLink;
                    }
                }

                if (shareText == null || shareText.trim().isEmpty()) {
                    shareText = fallbackShareLink();
                }

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.profile_action_share)));
            }

            @Override
            public void onFailure(Call<ProfileShareResponse> call, Throwable t) {
                String shareText = fallbackShareLink();

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.profile_action_share)));
            }
        });
    }

    private String extractShareableProfileLink(ProfileShareResponse response) {
        if (response == null) {
            return null;
        }

        String deepLink = response.getDeepLink();
        if (deepLink != null && !deepLink.trim().isEmpty()) {
            return deepLink.trim();
        }

        return null;
    }

    private String fallbackShareLink() {
        String safeId = currentUserId == null ? "" : currentUserId.trim();
        if (!safeId.isEmpty()) {
            return "instabond://profile?userId=" + safeId;
        }
        return "instabond://profile";
    }

    private String extractLinkFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(https?://\\S+|instabond://\\S+)").matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String link = matcher.group(1).trim();
        while (!link.isEmpty()) {
            char last = link.charAt(link.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ')' || last == ']' || last == '}') {
                link = link.substring(0, link.length() - 1);
            } else {
                break;
            }
        }
        return link;
    }

    private boolean isProfilePayload(Uri data) {
        return data.getQueryParameter("uid") != null
                || data.getQueryParameter("qr_uid") != null
                || data.getQueryParameter("payload") != null
                || "profile".equalsIgnoreCase(data.getHost())
                || data.toString().contains("instabond://profile");
    }

    private String extractDirectUserId(Uri data) {
        if (data == null) {
            return null;
        }

        if ("user".equalsIgnoreCase(data.getHost())) {
            String segment = data.getLastPathSegment();
            if (segment != null && !segment.trim().isEmpty()) {
                return segment.trim();
            }
        }

        String legacyPrefix = "instabond://user/";
        String raw = data.toString();
        if (raw.startsWith(legacyPrefix)) {
            String targetId = raw.substring(legacyPrefix.length()).trim();
            if (!targetId.isEmpty()) {
                return targetId;
            }
        }

        String userId = data.getQueryParameter("userId");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        return null;
    }

    private String extractResolvablePayload(Uri data) {
        if (data == null) {
            return null;
        }

        String qrUid = data.getQueryParameter("qr_uid");
        if (qrUid != null && !qrUid.trim().isEmpty()) {
            return qrUid.trim();
        }

        String uid = data.getQueryParameter("uid");
        if (uid != null && !uid.trim().isEmpty()) {
            return uid.trim();
        }

        String payload = data.getQueryParameter("payload");
        if (payload != null && !payload.trim().isEmpty()) {
            return payload.trim();
        }

        String raw = data.toString();
        Matcher matcher = Pattern.compile("(?:[?&#]|^)(qr_uid|uid|payload)=([^&#]+)").matcher(raw);
        if (matcher.find()) {
            String value = Uri.decode(matcher.group(2));
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return data.toString();
    }

    private String extractExternalPayloadFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }

        String[] keys = new String[] {"qr_uid", "uid", "payload", "profile_payload"};
        for (String key : keys) {
            String value = intent.getStringExtra(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null && !sharedText.trim().isEmpty()) {
            String extractedLink = extractLinkFromText(sharedText);
            if (extractedLink != null && !extractedLink.trim().isEmpty()) {
                return extractedLink.trim();
            }
            return sharedText.trim();
        }

        return null;
    }

    private void resolveProfileFromPayload(String payload, boolean shouldQuickFollowFromExternal) {
        if (payload == null || payload.trim().isEmpty()) {
            Toast.makeText(ProfileActivity.this, "Không thể mở hồ sơ từ QR", Toast.LENGTH_SHORT).show();
            return;
        }

        final String normalizedPayload = payload.trim();
        apiService.resolveProfile(normalizedPayload).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (response.code() == 404) {
                    Toast.makeText(ProfileActivity.this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(ProfileActivity.this, "Không thể mở hồ sơ từ QR", Toast.LENGTH_SHORT).show();
                    return;
                }

                UserProfileResponse profile = response.body();
                currentUserId = profile.getId();
                isOwnProfileView = currentUserId != null && currentUserId.equals(sessionManager.getUserId());

                if (shouldQuickFollowFromExternal) {
                    shouldAttemptQuickFollow = true;
                    quickFollowTargetUserId = currentUserId;
                }

                if (isOwnProfileView) {
                    configureOwnProfileView();
                } else {
                    configureExternalProfileView(currentUserId, false);
                }

                bindProfile(profile);
                maybeQuickFollowExternal(profile);
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void maybeQuickFollowExternal(UserProfileResponse profile) {
        if (!shouldAttemptQuickFollow || profile == null) {
            return;
        }

        String targetId = profile.getId();
        shouldAttemptQuickFollow = false;

        if (targetId == null || targetId.trim().isEmpty()) {
            quickFollowTargetUserId = null;
            return;
        }

        targetId = targetId.trim();
        if (quickFollowTargetUserId != null && !quickFollowTargetUserId.trim().isEmpty()
                && !targetId.equals(quickFollowTargetUserId.trim())) {
            quickFollowTargetUserId = null;
            return;
        }
        quickFollowTargetUserId = null;

        String myId = sessionManager.getUserId();
        if (myId != null && targetId.equals(myId.trim())) {
            return;
        }

        String relStatus = profile.getRelationshipStatus();
        if ("accepted".equals(relStatus) || "pending".equals(relStatus)) {
            return;
        }

        final String followTargetId = targetId;
        apiService.followUser(followTargetId).enqueue(new Callback<FollowUserResponse>() {
            @Override
            public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ProfileActivity.this, "Đã quick follow từ QR/link", Toast.LENGTH_SHORT).show();
                    loadUserProfile(followTargetId);
                }
            }

            @Override
            public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi mạng khi quick follow", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void showProfileOptionsMenu(String targetUserId) {
        android.view.View menuView = getLayoutInflater().inflate(R.layout.menu_profile_options, null);
        
        android.widget.LinearLayout itemBlock = menuView.findViewById(R.id.menu_block_user);
        android.widget.LinearLayout itemBack = menuView.findViewById(R.id.menu_back);
        
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(menuView);
        
        itemBlock.setOnClickListener(v -> {
            dialog.dismiss();
            showBlockUserConfirmation(targetUserId);
        });
        
        itemBack.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        
        dialog.show();
    }

    private void showBlockUserConfirmation(String userId) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle("Chặn người dùng")
            .setMessage("Bạn có chắc muốn chặn người dùng này?")
            .setPositiveButton("Chặn", (dialogInterface, which) -> blockUser(userId))
            .setNegativeButton("Hủy", null)
            .create();

        dialog.setOnShowListener(d -> {
            int textColor = ContextCompat.getColor(this, R.color.settings_text_primary);
            android.widget.Button negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(textColor);
            }
            if (positiveButton != null) {
                positiveButton.setTextColor(textColor);
            }
        });

        dialog.show();
    }

    private void blockUser(String userId) {
        apiService.blockUser(userId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ProfileActivity.this, "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ProfileActivity.this, "Chặn thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFriendSuggestions(String userId) {
        apiService.getFriendSuggestions(10).enqueue(new Callback<java.util.List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<java.util.List<FollowUserResponse>> call, 
                                 Response<java.util.List<FollowUserResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showSuggestionsDialog(response.body(), userId);
                }
            }

            @Override
            public void onFailure(Call<java.util.List<FollowUserResponse>> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi tải gợi ý", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSuggestionsDialog(java.util.List<FollowUserResponse> suggestions, String userId) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_friend_suggestions, null);
        androidx.recyclerview.widget.RecyclerView recyclerView = dialogView.findViewById(R.id.rv_suggestions);

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        FriendSuggestionAdapter adapter = new FriendSuggestionAdapter(suggestions, this);
        recyclerView.setAdapter(adapter);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle("Gợi ý kết bạn")
            .setView(dialogView)
            .setNegativeButton("Đóng", null)
            .create();

        dialog.setOnShowListener(d -> {
            int textColor = ContextCompat.getColor(this, R.color.settings_text_primary);
            android.widget.Button negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(textColor);
            }
        });
        
        dialog.show();
    }
}
