package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySettingsBinding;
import com.example.instabond_fe.model.ChangePasswordRequest;
import com.example.instabond_fe.model.UpdateAllowTaggingResponse;
import com.example.instabond_fe.model.UpdateProfileRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.utils.ThemePreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.io.IOException;

import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG_NONE = "none";
    private static final String TAG_EVERYONE = "everyone";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private ActivitySettingsBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private String userId;
    private boolean isUpdatingPrivacy;
    private boolean suppressPrivacyToggleListener;
    private boolean suppressThemeToggleListener;
    private boolean currentPrivacyState;
    private String currentTagPreference;

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
        syncThemeToggle();
        updateLanguageDisplay();
        setUiEnabled(false);
        loadMe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncThemeToggle();
        updateLanguageDisplay();
        loadMe();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveProfile.setOnClickListener(v -> updateProfile());
        binding.btnLogout.setOnClickListener(v -> logout());
        binding.btnHelpFab.setOnClickListener(v ->
                Toast.makeText(this, R.string.settings_feature_soon, Toast.LENGTH_SHORT).show());
        binding.btnBookmarks.setOnClickListener(v -> openBookmarks());
        binding.btnBlockedUsers.setOnClickListener(v -> openBlockedUsers());
        binding.btnTagPreference.setOnClickListener(v -> showTagPreferenceDialog());
        binding.btnLanguage.setOnClickListener(v -> showLanguageDialog());
        binding.btnChangePassword.setOnClickListener(v -> changePassword());

        binding.swPrivateAccount.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressPrivacyToggleListener || isUpdatingPrivacy) {
                return;
            }
            updatePrivacy(isChecked);
        });

        binding.swTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressThemeToggleListener) {
                return;
            }
            ThemePreferenceManager.setDarkModeEnabled(this, isChecked);
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
                    if (me.getId() != null) {
                        userId = me.getId();
                    }

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

                    currentTagPreference = normalizeTagPreference(me.getAllowTagging());
                    updateTagPreferenceUi();

                    setUiEnabled(true);
                } else {
                    setUiEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Could not load profile", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                setUiEnabled(true);
                Toast.makeText(
                        SettingsActivity.this,
                        getString(R.string.msg_connection_error, t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void showTagPreferenceDialog() {
        String[] options = {
                getString(R.string.tag_preference_none),
                getString(R.string.tag_preference_everyone)
        };
        String[] values = {TAG_NONE, TAG_EVERYONE};

        int checkedItem = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentTagPreference)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tag_preference_dialog_title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    updateTagPreferenceInstant(values[which]);
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
                    currentTagPreference = normalizeTagPreference(response.body().getAllowTagging());
                    updateTagPreferenceUi();
                    Toast.makeText(SettingsActivity.this, R.string.msg_profile_updated, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(
                            SettingsActivity.this,
                            getString(R.string.msg_update_error, getString(R.string.allow_tagging_label)),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<UpdateAllowTaggingResponse> call, Throwable t) {
                setUiEnabled(true);
                Toast.makeText(
                        SettingsActivity.this,
                        getString(R.string.msg_update_error, t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void updateTagPreferenceUi() {
        String displayValue;
        if (TAG_NONE.equals(currentTagPreference)) {
            displayValue = getString(R.string.tag_preference_none);
        } else if (TAG_EVERYONE.equals(currentTagPreference)) {
            displayValue = getString(R.string.tag_preference_everyone);
        } else {
            displayValue = "";
        }
        binding.tvTagPreferenceValue.setText(displayValue);
    }

    private String normalizeTagPreference(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        if (TAG_EVERYONE.equals(value)) {
            return TAG_EVERYONE;
        }
        if (TAG_NONE.equals(value)) {
            return TAG_NONE;
        }
        return null;
    }

    private UpdateProfileRequest createRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(binding.etFullName.getText().toString().trim());
        request.setBio(binding.etBio.getText().toString().trim());
        request.setPhoneNumber(binding.etPhoneNumber.getText().toString().trim());

        UpdateProfileRequest.SettingsRequest settings = new UpdateProfileRequest.SettingsRequest();
        String normalizedTagPreference = normalizeTagPreference(currentTagPreference);
        if (normalizedTagPreference != null) {
            settings.setAllowTagging(normalizedTagPreference);
        }
        settings.setTheme(ThemePreferenceManager.isDarkModeEnabled(this) ? "dark" : "light");
        request.setSettings(settings);

        return request;
    }

    private void updateProfile() {
        if (userId == null) {
            return;
        }

        setUiEnabled(false);
        apiService.updateProfile(userId, createRequest()).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                setUiEnabled(true);
                Toast.makeText(
                        SettingsActivity.this,
                        response.isSuccessful() ? R.string.msg_profile_updated : R.string.msg_profile_update_failed,
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                setUiEnabled(true);
                Toast.makeText(
                        SettingsActivity.this,
                        getString(R.string.msg_connection_error, t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
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
                        Toast.makeText(
                                SettingsActivity.this,
                                serverPrivacy ? "Private mode enabled" : "Private mode disabled",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        setPrivacyToggleCheckedSilently(currentPrivacyState);
                        setPrivacyToggleEnabled(true);
                    }
                } else {
                    setPrivacyToggleCheckedSilently(currentPrivacyState);
                    setPrivacyToggleEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Could not update privacy", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
                isUpdatingPrivacy = false;
                setPrivacyToggleCheckedSilently(currentPrivacyState);
                setPrivacyToggleEnabled(true);
                Toast.makeText(
                        SettingsActivity.this,
                        getString(R.string.msg_connection_error, t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void changePassword() {
        String currentPassword = binding.etCurrentPassword.getText().toString().trim();
        String newPassword = binding.etNewPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmNewPassword.getText().toString().trim();
        clearPasswordInputErrors();

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            if (currentPassword.isEmpty()) {
                binding.etCurrentPassword.setError(getString(R.string.settings_change_password_required_field));
            }
            if (newPassword.isEmpty()) {
                binding.etNewPassword.setError(getString(R.string.settings_change_password_required_field));
            }
            if (confirmPassword.isEmpty()) {
                binding.etConfirmNewPassword.setError(getString(R.string.settings_change_password_required_field));
            }
            Toast.makeText(this, R.string.settings_change_password_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPassword.length() < 8) {
            binding.etNewPassword.setError(getString(R.string.settings_change_password_length));
            Toast.makeText(this, R.string.settings_change_password_length, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            binding.etConfirmNewPassword.setError(getString(R.string.settings_change_password_mismatch));
            Toast.makeText(this, R.string.settings_change_password_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPassword.equals(newPassword)) {
            binding.etNewPassword.setError(getString(R.string.settings_change_password_same));
            Toast.makeText(this, R.string.settings_change_password_same, Toast.LENGTH_SHORT).show();
            return;
        }

        setChangePasswordEnabled(false);
        apiService.changePassword(new ChangePasswordRequest(currentPassword, newPassword, confirmPassword))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        setChangePasswordEnabled(true);

                        if (response.code() == 401) {
                            handleUnauthorized();
                            return;
                        }

                        if (response.isSuccessful()) {
                            binding.etCurrentPassword.setText("");
                            binding.etNewPassword.setText("");
                            binding.etConfirmNewPassword.setText("");
                            clearPasswordInputErrors();
                            Toast.makeText(SettingsActivity.this, R.string.settings_change_password_success, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String errorText = getErrorText(response.errorBody());
                        if (errorText == null || errorText.isEmpty()) {
                            errorText = getString(R.string.settings_change_password_failed);
                        }
                        applyPasswordErrorToField(errorText);
                        Toast.makeText(SettingsActivity.this, errorText, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        setChangePasswordEnabled(true);
                        Toast.makeText(
                                SettingsActivity.this,
                                getString(R.string.msg_connection_error, t.getMessage()),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private String getErrorText(ResponseBody errorBody) {
        if (errorBody == null) {
            return null;
        }
        try {
            String body = errorBody.string();
            if (body == null) {
                return null;
            }
            body = body.trim();
            if (body.startsWith("{")) {
                JSONObject json = new JSONObject(body);
                String message = json.optString("message", "").trim();
                if (!message.isEmpty()) {
                    return mapPasswordErrorMessage(message);
                }
            }
            return mapPasswordErrorMessage(body);
        } catch (IOException ignored) {
            return null;
        } catch (JSONException ignored) {
            return null;
        }
    }

    private String mapPasswordErrorMessage(String serverMessage) {
        if (serverMessage == null || serverMessage.trim().isEmpty()) {
            return getString(R.string.settings_change_password_failed);
        }

        String normalized = serverMessage.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("current password is incorrect")) {
            return getString(R.string.settings_change_password_wrong_current);
        }
        if (normalized.contains("new password and confirmation do not match")) {
            return getString(R.string.settings_change_password_mismatch);
        }
        if (normalized.contains("new password must be at least 8 characters")) {
            return getString(R.string.settings_change_password_length);
        }
        if (normalized.contains("new password must be different from current password")) {
            return getString(R.string.settings_change_password_same);
        }
        if (normalized.contains("required")) {
            return getString(R.string.settings_change_password_empty);
        }
        return serverMessage;
    }

    private void applyPasswordErrorToField(String errorText) {
        clearPasswordInputErrors();
        if (errorText == null) {
            return;
        }

        String normalized = errorText.toLowerCase(Locale.ROOT);
        if (normalized.contains(getString(R.string.settings_change_password_wrong_current).toLowerCase(Locale.ROOT))) {
            binding.etCurrentPassword.setError(errorText);
            return;
        }
        if (normalized.contains(getString(R.string.settings_change_password_mismatch).toLowerCase(Locale.ROOT))) {
            binding.etConfirmNewPassword.setError(errorText);
            return;
        }
        if (normalized.contains(getString(R.string.settings_change_password_length).toLowerCase(Locale.ROOT))
                || normalized.contains(getString(R.string.settings_change_password_same).toLowerCase(Locale.ROOT))) {
            binding.etNewPassword.setError(errorText);
        }
    }

    private void clearPasswordInputErrors() {
        binding.etCurrentPassword.setError(null);
        binding.etNewPassword.setError(null);
        binding.etConfirmNewPassword.setError(null);
    }

    private void setUiEnabled(boolean enabled) {
        binding.etFullName.setEnabled(enabled);
        binding.etBio.setEnabled(enabled);
        binding.etPhoneNumber.setEnabled(enabled);
        binding.etCurrentPassword.setEnabled(enabled);
        binding.etNewPassword.setEnabled(enabled);
        binding.etConfirmNewPassword.setEnabled(enabled);
        binding.btnSaveProfile.setEnabled(enabled);
        binding.btnSaveProfile.setAlpha(enabled ? 1.0f : 0.5f);
        setChangePasswordEnabled(enabled);
        binding.btnTagPreference.setEnabled(enabled);
        binding.btnTagPreference.setAlpha(enabled ? 1.0f : 0.7f);
        binding.swTheme.setEnabled(true);
        binding.swTheme.setAlpha(1.0f);
        setPrivacyToggleEnabled(enabled && !isUpdatingPrivacy);
    }

    private void setChangePasswordEnabled(boolean enabled) {
        binding.btnChangePassword.setEnabled(enabled);
        binding.btnChangePassword.setAlpha(enabled ? 1.0f : 0.5f);
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

    private void syncThemeToggle() {
        suppressThemeToggleListener = true;
        binding.swTheme.setChecked(ThemePreferenceManager.isDarkModeEnabled(this));
        suppressThemeToggleListener = false;
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
        startActivity(new Intent(this, BookmarksActivity.class));
    }

    private void openBlockedUsers() {
        startActivity(new Intent(this, BlockedUsersActivity.class));
    }

    private void showLanguageDialog() {
        String[] options = {
                getString(R.string.language_vietnamese),
                getString(R.string.language_english)
        };
        String[] values = {"vi", "en"};

        String currentLang = LocaleManager.getLanguage(this);
        int checkedItem = currentLang.equals("en") ? 1 : 0;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_label)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String newLang = values[which];
                    if (!newLang.equals(LocaleManager.getLanguage(this))) {
                        LocaleManager.setLocale(this, newLang);
                        updateLanguageDisplay();
                        LocaleManager.restartActivity(this);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.create_post_cancel, null)
                .show();
    }

    private void updateLanguageDisplay() {
        String currentLang = LocaleManager.getLanguage(this);
        binding.tvLanguageValue.setText(
                currentLang.equals("en") ? R.string.language_english : R.string.language_vietnamese
        );
    }
}
