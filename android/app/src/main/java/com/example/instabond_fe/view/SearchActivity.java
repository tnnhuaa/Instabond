package com.example.instabond_fe.view;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;

public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);
    }
}
