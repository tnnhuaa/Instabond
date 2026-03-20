package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySigninBinding;
import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignInActivity extends AppCompatActivity {

    private ActivitySigninBinding binding;
    private boolean passwordVisible = false;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySigninBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        binding.btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        binding.btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                binding.etPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                binding.etPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });

        binding.btnSignin.setOnClickListener(v -> performLogin());

        binding.tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.login_forgot_unavailable), Toast.LENGTH_SHORT).show());
    }

    private void performLogin() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.login_validation_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        AuthRequest request = AuthRequest.forLogin(email, password);
        apiService.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getAccessToken() != null) {
                    sessionManager.saveSession(response.body());
                    goToNewsfeed();
                    return;
                }

                Toast.makeText(SignInActivity.this,
                        extractError(response, getString(R.string.login_failed)),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(SignInActivity.this,
                        getString(R.string.login_connection_error, t.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnSignin.setEnabled(!loading);
        binding.btnSignin.setText(loading
                ? getString(R.string.login_loading)
                : getString(R.string.login_button_signin));
    }

    private String extractError(Response<?> response, String fallback) {
        if (response.errorBody() == null) {
            return fallback;
        }
        try {
            String raw = response.errorBody().string();
            return raw == null || raw.isEmpty() ? fallback : raw;
        } catch (IOException e) {
            return fallback;
        }
    }

    private void goToNewsfeed() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
