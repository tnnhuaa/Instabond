package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivityResetPasswordBinding;
import com.example.instabond_fe.model.ForgotPasswordRequest;
import com.example.instabond_fe.model.ResetPasswordRequest;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {
    private ActivityResetPasswordBinding binding;
    private ApiService apiService;
    private String userEmail;
    private CountDownTimer countDownTimer;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        userEmail = getIntent().getStringExtra("EMAIL");

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                binding.etNewPassword.setInputType(
                        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                binding.etNewPassword.setInputType(
                        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            binding.etNewPassword.setSelection(binding.etNewPassword.getText().length());
        });

        binding.btnConfirm.setOnClickListener(v -> handleResetPassword());
        binding.btnResend.setOnClickListener(v -> handleResendOtp());

        startResendTimer();
    }

    private void handleResetPassword() {
        String otp = binding.etOtp.getText().toString().trim();
        String newPassword = binding.etNewPassword.getText().toString();

        if (otp.length() != 6) {
            binding.etOtp.setError("OTP must be 6 digits");
            return;
        }
        if (newPassword.length() < 8) {
            binding.etNewPassword.setError("Password must be at least 8 characters");
            return;
        }

        setLoading(true);
        ResetPasswordRequest request = new ResetPasswordRequest(userEmail, otp, newPassword);
        apiService.resetPassword(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    Toast.makeText(ResetPasswordActivity.this, "Password changed successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ResetPasswordActivity.this, SignInActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);

                    finish();
                } else {
                    try {
                        String err = response.errorBody() != null ? response.errorBody().string() : "Verification failed";
                        Toast.makeText(ResetPasswordActivity.this, err, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ResetPasswordActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleResendOtp() {
        binding.btnResend.setEnabled(false);
        apiService.forgotPassword(new ForgotPasswordRequest(userEmail)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ResetPasswordActivity.this, "OTP code resent", Toast.LENGTH_SHORT).show();
                    startResendTimer();
                } else {
                    binding.btnResend.setEnabled(true);
                    Toast.makeText(ResetPasswordActivity.this, "Resend failed. Please try again later.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                binding.btnResend.setEnabled(true);
                Toast.makeText(ResetPasswordActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startResendTimer() {
        binding.btnResend.setEnabled(false);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.btnResend.setText("Resend (" + millisUntilFinished / 1000 + "s)");
            }

            @Override
            public void onFinish() {
                binding.btnResend.setText("Resend code");
                binding.btnResend.setEnabled(true);
            }
        }.start();
    }

    private void setLoading(boolean isLoading) {
        binding.btnConfirm.setEnabled(!isLoading);
        binding.etOtp.setEnabled(!isLoading);
        binding.etNewPassword.setEnabled(!isLoading);
        binding.tvConfirmCta.setText(isLoading ? "Processing..." : "Confirm");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}