package com.example.instabond_fe.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.instabond_fe.model.AuthResponse;

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

        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, response.getAccessToken())
                .putString(KEY_REFRESH_TOKEN, response.getRefreshToken())
                .putString(KEY_USER_ID, response.getUserId())
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}

