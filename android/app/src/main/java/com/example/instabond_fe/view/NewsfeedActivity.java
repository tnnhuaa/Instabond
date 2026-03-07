package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.databinding.ActivityMainBinding;
import com.example.instabond_fe.model.Post;

import java.util.List;

public class NewsfeedActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Toolbar
        setSupportActionBar(binding.toolbar);

        // RecyclerView feed with mock data
        List<Post> posts = Post.getMockPosts();
        PostAdapter adapter = new PostAdapter(posts);
        binding.rvFeed.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeed.setAdapter(adapter);

        // Bottom Navigation
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.example.instabond_fe.R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return true;
        });
    }
}

