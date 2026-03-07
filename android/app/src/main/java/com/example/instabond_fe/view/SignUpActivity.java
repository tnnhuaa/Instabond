package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySignupBinding;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
        binding.btnSignup.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString();
            String confirm = binding.etConfirmPassword.getText().toString();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, "Mật khẩu nhập lại không khớp", Toast.LENGTH_SHORT).show();
                return;
            }
            // Mock registration success → go to Newsfeed
            Toast.makeText(this, "Đăng ký thành công! Xin chào " + username, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, NewsfeedActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // "Already have account? Sign In" link
        binding.tvLoginNow.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }
}

