package com.instabond.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "OTP:REGISTER:";
    private static final int OTP_EXPIRATION_MINUTES = 5;

    public String generateAndSaveOtp(String email) {
        String otpCode = generateRandomOtp(6);

        String key = OTP_PREFIX + email;

        // Save OTP to Redis with expiration time (5 mins)
        redisTemplate.opsForValue().set(key, otpCode, Duration.ofMinutes(OTP_EXPIRATION_MINUTES));

        return otpCode;
    }

    public boolean verifyOtp(String email, String inputOtp) {
        String key = OTP_PREFIX + email;

        String savedOtp = redisTemplate.opsForValue().get(key);

        if (savedOtp != null && savedOtp.equals(inputOtp)) {
            redisTemplate.delete(key);
            return true;
        }

        return false;
    }

    // === HELPERS ===
    private String generateRandomOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
