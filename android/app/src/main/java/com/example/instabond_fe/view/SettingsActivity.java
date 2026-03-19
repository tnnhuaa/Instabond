package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySettingsBinding;
import com.example.instabond_fe.model.UpdateProfileRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private String userId;

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
        setUiEnabled(false);
        loadMe();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveProfile.setOnClickListener(v -> updateProfile());
        binding.btnLogout.setOnClickListener(v -> logout());
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

                    binding.etFullName.setText(me.getFullName() != null ? me.getFullName() : "");
                    binding.etBio.setText(me.getBio() != null ? me.getBio() : "");
                    binding.etPhoneNumber.setText(me.getPhoneNumber() != null ? me.getPhoneNumber() : "");

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


    private UpdateProfileRequest createRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(binding.etFullName.getText().toString().trim());
        request.setBio(binding.etBio.getText().toString().trim());
        request.setPhoneNumber(binding.etPhoneNumber.getText().toString().trim());

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

    private void setUiEnabled(boolean enabled) {
        binding.etFullName.setEnabled(enabled);
        binding.etBio.setEnabled(enabled);
        binding.etPhoneNumber.setEnabled(enabled);
        binding.btnSaveProfile.setEnabled(enabled);
        binding.btnSaveProfile.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void handleUnauthorized() {
        sessionManager.clearSession();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logout() {
        sessionManager.clearSession();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}