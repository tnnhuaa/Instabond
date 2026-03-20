package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySigninBinding;
import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;

import org.json.JSONObject;

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

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        // Tab: switch to Sign Up
        binding.tvTabSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        // Toggle password visibility
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

        // Sign In button
        binding.btnSignin.setOnClickListener(v -> performLogin());

        // Google Sign In (mock)
        binding.btnGoogle.setOnClickListener(v ->
                Toast.makeText(this, "Đăng nhập Google chưa được tích hợp backend", Toast.LENGTH_SHORT).show());

        // "Register now" link
        binding.tvRegisterNow.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
    }

    private void performLogin() {
        String email = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        clearFieldErrors();
        if (!validateInput(email, password)) {
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
                        extractError(response, "Đăng nhập thất bại"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                String message = t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "Vui lòng thử lại"
                        : t.getMessage();
                Toast.makeText(SignInActivity.this,
                        "Không kết nối được server: " + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validateInput(String email, String password) {
        if (email.isEmpty()) {
            binding.etUsername.setError("Vui lòng nhập email");
            binding.etUsername.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etUsername.setError("Email không hợp lệ");
            binding.etUsername.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError("Vui lòng nhập mật khẩu");
            binding.etPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void clearFieldErrors() {
        binding.etUsername.setError(null);
        binding.etPassword.setError(null);
    }

    private void setLoading(boolean loading) {
        binding.btnSignin.setEnabled(!loading);
        binding.btnSignin.setText(loading ? "Đang đăng nhập..." : "Đăng nhập");
        binding.etUsername.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.btnTogglePassword.setEnabled(!loading);
        binding.tvTabSignup.setEnabled(!loading);
        binding.tvRegisterNow.setEnabled(!loading);
        binding.btnGoogle.setEnabled(!loading);
    }

    private String extractError(Response<?> response, String fallback) {
        if (response.errorBody() == null) {
            return fallback;
        }
        try {
            String raw = response.errorBody().string();
            if (raw == null || raw.isEmpty()) {
                return fallback;
            }
            JSONObject json = new JSONObject(raw);
            String message = json.optString("message");
            return message == null || message.trim().isEmpty() ? fallback : message;
        } catch (IOException e) {
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void goToNewsfeed() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
