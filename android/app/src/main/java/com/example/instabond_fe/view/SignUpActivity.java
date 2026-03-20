package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySignupBinding;
import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;

import java.io.IOException;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        // Tab: switch to Sign In
        binding.tvTabSignin.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
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

        // Toggle confirm password visibility
        binding.btnToggleConfirmPassword.setOnClickListener(v -> {
            confirmPasswordVisible = !confirmPasswordVisible;
            if (confirmPasswordVisible) {
                binding.etConfirmPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                binding.etConfirmPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.getText().length());
        });

        // Upload photo (mock)
        binding.btnUploadPhoto.setOnClickListener(v ->
                Toast.makeText(this, "Chức năng tải ảnh (mock)", Toast.LENGTH_SHORT).show());

        // Sign Up button
        binding.btnSignup.setOnClickListener(v -> performRegister());

        // "Already have account? Sign In" link
        binding.tvLoginNow.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }

    private void performRegister() {
        String username = binding.etUsername.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();
        String confirm = binding.etConfirmPassword.getText().toString();

        clearFieldErrors();
        if (!validateInput(username, email, password, confirm)) {
            return;
        }

        setLoading(true);
        AuthRequest request = AuthRequest.forRegister(username, email, password, null);
        apiService.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getAccessToken() != null) {
                    sessionManager.saveSession(response.body());
                    Toast.makeText(SignUpActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                    goToNewsfeed();
                    return;
                }

                Toast.makeText(SignUpActivity.this,
                        extractError(response, "Đăng ký thất bại"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                String message = t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "Vui lòng thử lại"
                        : t.getMessage();
                Toast.makeText(SignUpActivity.this,
                        "Không kết nối được server: " + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnSignup.setEnabled(!loading);
        binding.btnSignup.setText(loading ? "Đang đăng ký..." : "Đăng ký");
        binding.etUsername.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);
        binding.btnTogglePassword.setEnabled(!loading);
        binding.btnToggleConfirmPassword.setEnabled(!loading);
        binding.btnUploadPhoto.setEnabled(!loading);
        binding.tvTabSignin.setEnabled(!loading);
        binding.tvLoginNow.setEnabled(!loading);
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

    private boolean validateInput(String username, String email, String password, String confirm) {
        if (username.isEmpty()) {
            binding.etUsername.setError("Vui lòng nhập tên đăng nhập");
            binding.etUsername.requestFocus();
            return false;
        }

        if (username.length() < 4) {
            binding.etUsername.setError("Tên đăng nhập tối thiểu 4 ký tự");
            binding.etUsername.requestFocus();
            return false;
        }
        
        if (email.isEmpty()) {
            binding.etEmail.setError("Vui lòng nhập email");
            binding.etEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Email không hợp lệ");
            binding.etEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError("Vui lòng nhập mật khẩu");
            binding.etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            binding.etPassword.setError("Mật khẩu tối thiểu 6 ký tự");
            binding.etPassword.requestFocus();
            return false;
        }

        if (confirm.isEmpty()) {
            binding.etConfirmPassword.setError("Vui lòng nhập lại mật khẩu");
            binding.etConfirmPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirm)) {
            binding.etConfirmPassword.setError("Mật khẩu nhập lại không khớp");
            binding.etConfirmPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void clearFieldErrors() {
        binding.etUsername.setError(null);
        binding.etEmail.setError(null);
        binding.etPassword.setError(null);
        binding.etConfirmPassword.setError(null);
    }

    private void goToNewsfeed() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
