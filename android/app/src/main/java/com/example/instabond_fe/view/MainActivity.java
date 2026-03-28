package com.example.instabond_fe.view;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.network.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);
        Class<?> destination = sessionManager.isLoggedIn() ? NewsfeedActivity.class : SignInActivity.class;

        Intent intent = new Intent(this, destination);
        if (!canResolve(intent)) {
            sessionManager.clearSession();
            intent = new Intent(this, SignInActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean canResolve(Intent intent) {
        PackageManager packageManager = getPackageManager();
        return intent.resolveActivity(packageManager) != null;
    }
}
