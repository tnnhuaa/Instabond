package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;

public class SearchActivity extends AppCompatActivity {

    private static final String FEATURED_LEFT =
            "https://www.figma.com/api/mcp/asset/6b410331-ffd2-4d80-81e8-d7e87f10b00b";
    private static final String TOP_RIGHT =
            "https://www.figma.com/api/mcp/asset/793ab595-8a0f-43d6-837b-d2175dd49f07";
    private static final String MID_RIGHT =
            "https://www.figma.com/api/mcp/asset/cf7fb7a4-3e52-47a0-b6ac-5288c73170da";
    private static final String ROW_THREE_LEFT =
            "https://www.figma.com/api/mcp/asset/afba4829-ff0b-4812-b672-e25279fe1ee8";
    private static final String ROW_THREE_RIGHT =
            "https://www.figma.com/api/mcp/asset/01239621-fa36-4420-899c-1f77417d677e";
    private static final String ROW_FOUR_LEFT =
            "https://www.figma.com/api/mcp/asset/a6e2c84e-ae2b-4f31-a8e5-0915ee6edca5";
    private static final String ROW_FOUR_RIGHT =
            "https://www.figma.com/api/mcp/asset/c0f072dc-b246-49de-9e73-3ab4771e95ae";
    private static final String BOTTOM_LEFT =
            "https://www.figma.com/api/mcp/asset/bd933190-a8ce-4f6a-83f9-8ef3e1ba1206";

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);
        bindImages();
        bindActions();
    }

    private void bindImages() {
        loadImage(binding.ivFeaturedLeft, FEATURED_LEFT);
        loadImage(binding.ivTopRight, TOP_RIGHT);
        loadImage(binding.ivMidRight, MID_RIGHT);
        loadImage(binding.ivRowThreeLeft, ROW_THREE_LEFT);
        loadImage(binding.ivRowThreeRight, ROW_THREE_RIGHT);
        loadImage(binding.ivRowFourLeft, ROW_FOUR_LEFT);
        loadImage(binding.ivRowFourRight, ROW_FOUR_RIGHT);
        loadImage(binding.ivBottomLeft, BOTTOM_LEFT);
    }

    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
    }

    private void loadImage(ImageView target, String url) {
        Glide.with(this)
                .load(url)
                .centerCrop()
                .into(target);
    }
}
