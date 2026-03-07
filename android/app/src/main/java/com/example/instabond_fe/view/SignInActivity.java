package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySigninBinding;

public class SignInActivity extends AppCompatActivity {

    private ActivitySigninBinding binding;
    private boolean passwordVisible = false;

    // Mock credentials
    private static final String MOCK_USERNAME = "admin";
    private static final String MOCK_PASSWORD = "123456";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySigninBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
        binding.btnSignin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }
            // Mock authentication
            if (username.equals(MOCK_USERNAME) && password.equals(MOCK_PASSWORD)) {
                goToNewsfeed();
            } else {
                Toast.makeText(this, "Tên đăng nhập hoặc mật khẩu không đúng", Toast.LENGTH_SHORT).show();
            }
        });

        // Google Sign In (mock)
        binding.btnGoogle.setOnClickListener(v -> {
            Toast.makeText(this, "Đăng nhập với Google (mock)", Toast.LENGTH_SHORT).show();
            goToNewsfeed();
        });

        // "Register now" link
        binding.tvRegisterNow.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
    }

    private void goToNewsfeed() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}

