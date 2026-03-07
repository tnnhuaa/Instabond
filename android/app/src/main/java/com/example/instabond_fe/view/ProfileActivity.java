package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivityProfileBinding;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Toolbar (AppBarLayout uses its own toolbar — no setSupportActionBar needed)
        binding.toolbar.setTitle("");

        // Settings button
        binding.btnSettings.setOnClickListener(v ->
                Toast.makeText(this, "Cài đặt (mock)", Toast.LENGTH_SHORT).show());

        // Bottom nav: Home → back to Newsfeed
        binding.navHome.setOnClickListener(v -> {
            startActivity(new Intent(this, NewsfeedActivity.class));
            finish();
        });

        // Create FAB (mock)
        binding.btnCreate.setOnClickListener(v ->
                Toast.makeText(this, "Tạo bài viết (mock)", Toast.LENGTH_SHORT).show());

        // Grid / Tagged tabs (mock)
        binding.tabGrid.setOnClickListener(v ->
                Toast.makeText(this, "Lưới ảnh", Toast.LENGTH_SHORT).show());
        binding.tabTagged.setOnClickListener(v ->
                Toast.makeText(this, "Có mặt tôi", Toast.LENGTH_SHORT).show());
    }
}

