package com.example.instabond_fe.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferenceManager {
    private static final String PREF_NAME = "instabond_theme";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemePreferenceManager() {
    }

    public static void applySavedTheme(Context context) {
        int nightMode = getNightMode(context);
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    public static boolean isDarkModeEnabled(Context context) {
        return getNightMode(context) == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        int nightMode = enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        getPrefs(context).edit().putInt(KEY_NIGHT_MODE, nightMode).apply();
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    private static int getNightMode(Context context) {
        return getPrefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
