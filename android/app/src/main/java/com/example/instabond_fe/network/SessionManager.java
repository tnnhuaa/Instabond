package com.example.instabond_fe.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.example.instabond_fe.model.AuthResponse;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class SessionManager {
    private static final String PREF_NAME = "instabond_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(AuthResponse response) {
        if (response == null) {
            return;
        }

        String resolvedUserId = response.getUserId();
        if (resolvedUserId == null || resolvedUserId.isEmpty()) {
            resolvedUserId = extractUserIdFromToken(response.getAccessToken());
        }

        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, response.getAccessToken())
                .putString(KEY_REFRESH_TOKEN, response.getRefreshToken())
                .putString(KEY_USER_ID, resolvedUserId)
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        String storedUserId = prefs.getString(KEY_USER_ID, null);
        if (storedUserId != null && !storedUserId.isEmpty()) {
            return storedUserId;
        }

        String resolvedFromToken = extractUserIdFromToken(getAccessToken());
        if (resolvedFromToken != null && !resolvedFromToken.isEmpty()) {
            prefs.edit().putString(KEY_USER_ID, resolvedFromToken).apply();
        }
        return resolvedFromToken;
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    private String extractUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            String payload = parts[1]
                    .replace('-', '+')
                    .replace('_', '/');

            int remainder = payload.length() % 4;
            if (remainder > 0) {
                payload = payload + "=".repeat(4 - remainder);
            }

            byte[] decoded = Base64.decode(payload, Base64.DEFAULT);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            String[] candidates = {"userId", "user_id", "id", "sub"};
            for (String key : candidates) {
                String value = obj.optString(key, "");
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }
}
