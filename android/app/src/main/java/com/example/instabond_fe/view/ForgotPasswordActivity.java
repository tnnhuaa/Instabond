package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivityForgotPasswordBinding;
import com.example.instabond_fe.model.ForgotPasswordRequest;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import org.json.JSONObject;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {
    private ActivityForgotPasswordBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendOtp.setOnClickListener(v -> handleSendOtp());
    }

    private void handleSendOtp() {
        String email = binding.etEmail.getText().toString().trim();

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Invalid email address");
            return;
        }

        setLoading(true);
        apiService.forgotPassword(new ForgotPasswordRequest(email)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    Intent intent = new Intent(ForgotPasswordActivity.this, ResetPasswordActivity.class);
                    intent.putExtra("EMAIL", email);
                    startActivity(intent);
                } else {
                    showError(response);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ForgotPasswordActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.btnSendOtp.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.tvSendOtpCta.setText(isLoading ? "Sending..." : "Send OTP");
    }

    private void showError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String error = response.errorBody().string();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show();
        }
    }
}