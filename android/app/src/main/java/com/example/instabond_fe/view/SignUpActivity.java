package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySignupBinding;
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

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private boolean passwordVisible = false;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        binding.btnBack.setOnClickListener(v -> navigateToSignIn());
        binding.tvLoginNow.setOnClickListener(v -> navigateToSignIn());

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

        binding.btnSignup.setOnClickListener(v -> performRegister());
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
                    Toast.makeText(SignUpActivity.this, getString(R.string.signup_success), Toast.LENGTH_SHORT).show();
                    goToNewsfeed();
                    return;
                }

                Toast.makeText(SignUpActivity.this,
                        extractError(response, getString(R.string.signup_failed)),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                String message = t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "Vui long thu lai"
                        : t.getMessage();
                Toast.makeText(SignUpActivity.this,
                        getString(R.string.signup_connection_error, message),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validateInput(String username, String email, String password, String confirm) {
        if (username.isEmpty()) {
            binding.etUsername.setError("Vui long nhap ten dang nhap");
            binding.etUsername.requestFocus();
            return false;
        }

        if (username.length() < 4) {
            binding.etUsername.setError("Ten dang nhap toi thieu 4 ky tu");
            binding.etUsername.requestFocus();
            return false;
        }

        if (email.isEmpty()) {
            binding.etEmail.setError("Vui long nhap email");
            binding.etEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Email khong hop le");
            binding.etEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError("Vui long nhap mat khau");
            binding.etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            binding.etPassword.setError("Mat khau toi thieu 6 ky tu");
            binding.etPassword.requestFocus();
            return false;
        }

        if (confirm.isEmpty()) {
            binding.etConfirmPassword.setError("Vui long nhap lai mat khau");
            binding.etConfirmPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirm)) {
            binding.etConfirmPassword.setError("Mat khau nhap lai khong khop");
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

    private void setLoading(boolean loading) {
        binding.btnSignup.setEnabled(!loading);
        binding.tvSignupCta.setText(loading
                ? getString(R.string.signup_loading)
                : getString(R.string.signup_button));
        binding.etUsername.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);
        binding.btnTogglePassword.setEnabled(!loading);
        binding.tvLoginNow.setEnabled(!loading);
        binding.btnBack.setEnabled(!loading);
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

    private void navigateToSignIn() {
        startActivity(new Intent(this, SignInActivity.class));
        finish();
    }

    private void goToNewsfeed() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
