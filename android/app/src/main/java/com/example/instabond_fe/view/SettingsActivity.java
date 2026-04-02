package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySettingsBinding;
import com.example.instabond_fe.model.UpdateAllowTaggingResponse;
import com.example.instabond_fe.model.UpdateProfileRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.utils.AvatarLoader;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private String userId;
    private boolean isUpdatingPrivacy;
    private boolean suppressPrivacyToggleListener;
    private boolean currentPrivacyState;
    private String currentTagPreference = "everyone";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        userId = getIntent().getStringExtra("user_id");
        if (userId == null || userId.trim().isEmpty()) {
            userId = sessionManager.getUserId();
        }

        setupListeners();
        binding.swTheme.setChecked(false);
        setUiEnabled(false);
        loadMe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMe();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveProfile.setOnClickListener(v -> updateProfile());
        binding.btnLogout.setOnClickListener(v -> logout());
        binding.btnEditAvatar.setOnClickListener(v ->
                Toast.makeText(this, R.string.settings_feature_soon, Toast.LENGTH_SHORT).show());
        binding.btnHelpFab.setOnClickListener(v ->
                Toast.makeText(this, R.string.settings_feature_soon, Toast.LENGTH_SHORT).show());
        binding.btnBookmarks.setOnClickListener(v -> openBookmarks());
        binding.btnBlockedUsers.setOnClickListener(v -> openBlockedUsers());
        binding.swPrivateAccount.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressPrivacyToggleListener || isUpdatingPrivacy) {
                return;
            }
            updatePrivacy(isChecked);
        });
        binding.btnTagPreference.setOnClickListener(v -> showTagPreferenceDialog());
        binding.swTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            Toast.makeText(this, R.string.settings_feature_soon, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadMe() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse me = response.body();
                    if (me.getId() != null) userId = me.getId();

                    String fullName = me.getFullName() != null && !me.getFullName().trim().isEmpty()
                            ? me.getFullName().trim()
                            : getString(R.string.app_name);
                    String username = me.getUsername() != null && !me.getUsername().trim().isEmpty()
                            ? "@" + me.getUsername().trim().replace("@", "")
                            : "@instabond";

                    binding.tvDisplayName.setText(fullName);
                    binding.tvUsername.setText(username);
                    AvatarLoader.load(binding.ivAvatar, me.getAvatarUrl());
                    binding.etFullName.setText(me.getFullName() != null ? me.getFullName() : "");
                    binding.etBio.setText(me.getBio() != null ? me.getBio() : "");
                    binding.etPhoneNumber.setText(me.getPhoneNumber() != null ? me.getPhoneNumber() : "");
                    applyPrivacyState(me.isPrivate());

                    if (me.getAllowTagging() != null) {
                        currentTagPreference = me.getAllowTagging();
                        updateTagPreferenceUi();
                    }

                    setUiEnabled(true);
                } else {
                    Toast.makeText(SettingsActivity.this, "Không thể tải dữ liệu người dùng", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showTagPreferenceDialog() {
        String[] options = {
                getString(R.string.tag_preference_none),
                getString(R.string.tag_preference_everyone)
        };
        String[] values = {"none", "everyone"};

        int checkedItem = 1; // Default everyone
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(currentTagPreference)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tag_preference_dialog_title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selectedValue = values[which];
                    updateTagPreferenceInstant(selectedValue);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.create_post_cancel, null)
                .show();
    }

    private void updateTagPreferenceInstant(String newValue) {
        setUiEnabled(false);
        apiService.updateAllowTagging(newValue).enqueue(new Callback<UpdateAllowTaggingResponse>() {
            @Override
            public void onResponse(Call<UpdateAllowTaggingResponse> call, Response<UpdateAllowTaggingResponse> response) {
                setUiEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    currentTagPreference = response.body().getAllowTagging();
                    updateTagPreferenceUi();
                    Toast.makeText(SettingsActivity.this, "Đã cập nhật tùy chọn gắn thẻ", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, "Không thể cập nhật tùy chọn gắn thẻ", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UpdateAllowTaggingResponse> call, Throwable t) {
                setUiEnabled(true);
                Toast.makeText(SettingsActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTagPreferenceUi() {
        String displayValue;
        if ("none".equalsIgnoreCase(currentTagPreference)) {
            displayValue = getString(R.string.tag_preference_none);
        } else {
            displayValue = getString(R.string.tag_preference_everyone);
        }
        binding.tvTagPreferenceValue.setText(displayValue);
    }

    private UpdateProfileRequest createRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(binding.etFullName.getText().toString().trim());
        request.setBio(binding.etBio.getText().toString().trim());
        request.setPhoneNumber(binding.etPhoneNumber.getText().toString().trim());

        UpdateProfileRequest.SettingsRequest settings = new UpdateProfileRequest.SettingsRequest();
        settings.setAllowTagging(currentTagPreference.toLowerCase());
        request.setSettings(settings);

        return request;
    }

    private void updateProfile() {
        if (userId == null) return;

        setUiEnabled(false);
        apiService.updateProfile(userId, createRequest()).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                setUiEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(SettingsActivity.this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, "Lưu thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                setUiEnabled(true);
                Toast.makeText(SettingsActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePrivacy(boolean makePrivate) {
        isUpdatingPrivacy = true;
        setPrivacyToggleEnabled(false);

        Call<UserProfileResponse> request = makePrivate
                ? apiService.enablePrivateMode()
                : apiService.disablePrivateMode();

        request.enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                isUpdatingPrivacy = false;

                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (response.isSuccessful()) {
                    UserProfileResponse body = response.body();
                    if (body != null) {
                        boolean serverPrivacy = body.isPrivate();
                        applyPrivacyState(serverPrivacy);
                        setPrivacyToggleEnabled(true);
                        Toast.makeText(SettingsActivity.this,
                                serverPrivacy ? "Tài khoản đã chuyển sang riêng tư" : "Tài khoản đã chuyển sang công khai",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        setPrivacyToggleCheckedSilently(currentPrivacyState);
                        setPrivacyToggleEnabled(true);
                    }
                } else {
                    setPrivacyToggleCheckedSilently(currentPrivacyState);
                    setPrivacyToggleEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Không thể cập nhật quyền riêng tư", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                isUpdatingPrivacy = false;
                setPrivacyToggleCheckedSilently(currentPrivacyState);
                setPrivacyToggleEnabled(true);
                Toast.makeText(SettingsActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setUiEnabled(boolean enabled) {
        binding.etFullName.setEnabled(enabled);
        binding.etBio.setEnabled(enabled);
        binding.etPhoneNumber.setEnabled(enabled);
        binding.btnSaveProfile.setEnabled(enabled);
        binding.btnSaveProfile.setAlpha(enabled ? 1.0f : 0.5f);
        binding.btnTagPreference.setEnabled(enabled);
        binding.swTheme.setEnabled(enabled);
        binding.btnTagPreference.setAlpha(enabled ? 1.0f : 0.7f);
        binding.swTheme.setAlpha(1.0f);
        setPrivacyToggleEnabled(enabled && !isUpdatingPrivacy);
    }

    private void setPrivacyToggleEnabled(boolean enabled) {
        binding.swPrivateAccount.setEnabled(enabled);
        binding.swPrivateAccount.setAlpha(1.0f);
    }

    private void setPrivacyToggleCheckedSilently(boolean checked) {
        suppressPrivacyToggleListener = true;
        binding.swPrivateAccount.setChecked(checked);
        suppressPrivacyToggleListener = false;
    }

    private void applyPrivacyState(boolean isPrivate) {
        currentPrivacyState = isPrivate;
        setPrivacyToggleCheckedSilently(isPrivate);
    }

    private void handleUnauthorized() {
        ChatRepository.getInstance(this).disconnectRealtime();
        sessionManager.clearSession();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logout() {
        ChatRepository.getInstance(this).disconnectRealtime();
        sessionManager.clearSession();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openBookmarks() {
        Intent intent = new Intent(this, BookmarksActivity.class);
        startActivity(intent);
    }

    private void openBlockedUsers() {
        Intent intent = new Intent(this, BlockedUsersActivity.class);
        startActivity(intent);
    }
}
