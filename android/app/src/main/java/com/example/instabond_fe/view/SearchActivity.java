package com.example.instabond_fe.view;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;

public class SearchActivity extends AppCompatActivity {

    private static final String[] EXPLORE_IMAGE_URLS = {
            "https://www.figma.com/api/mcp/asset/7d0ba4af-531c-4f8c-8a6a-734db6d7790a",
            "https://www.figma.com/api/mcp/asset/ec67a372-a07e-4d7f-bb74-03ca2a6aaa96",
            "https://www.figma.com/api/mcp/asset/01ac25df-9fc3-459e-807f-daea4c67daab",
            "https://www.figma.com/api/mcp/asset/1cf80694-8149-4cc8-8d83-3b47681a3ccc",
            "https://www.figma.com/api/mcp/asset/5058299d-5420-4200-89fe-9ee6f7d2aca5",
            "https://www.figma.com/api/mcp/asset/01fe20c1-5843-4d9c-b977-dc54148ed23e",
            "https://www.figma.com/api/mcp/asset/526b352e-2924-4264-b4b9-09e0b49c0979",
            "https://www.figma.com/api/mcp/asset/c0b1ad35-fcc9-4499-8e49-2c4d4325f5eb"
    };

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);
    }
}
