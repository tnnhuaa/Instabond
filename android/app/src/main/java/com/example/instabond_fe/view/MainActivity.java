package com.example.instabond_fe.view;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Thiết lập Toolbar
        setSupportActionBar(binding.toolbar);

        // Thiết lập Bottom Navigation
        binding.bottomNav.setOnItemSelectedListener(item -> {
            // Xử lý chuyển tab sau này
            return true;
        });
    }
}