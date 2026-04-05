package com.example.instabond_fe.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityTagUserBinding;
import com.example.instabond_fe.model.SuggestedTag;
import java.util.ArrayList;

public class TagUserActivity extends AppCompatActivity {

    private ActivityTagUserBinding binding;
    private ArrayList<SuggestedTag> taggedUsers = new ArrayList<>();
    private float currentTapX = 0f;
    private float currentTapY = 0f;
    private TaggedUserAdapter taggedUserAdapter;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTagUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String imageUriStr = getIntent().getStringExtra("IMAGE_URI");
        if (imageUriStr == null || imageUriStr.trim().isEmpty()) {
            Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getIntent().hasExtra("TAGGED_USERS")) {
            Object serializable = getIntent().getSerializableExtra("TAGGED_USERS");
            if (serializable instanceof ArrayList<?>) {
                taggedUsers = (ArrayList<SuggestedTag>) serializable;
            }
        }

        Glide.with(this).load(Uri.parse(imageUriStr)).into(binding.ivMainImage);
        renderBadges();

        // Setup RecyclerView rv_tagged_list
        taggedUserAdapter = new TaggedUserAdapter(true);
        binding.rvTaggedList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTaggedList.setAdapter(taggedUserAdapter);

        taggedUserAdapter.setTaggedUsers(taggedUsers);

        taggedUserAdapter.setOnRemoveClickListener((tag, position) -> {
            taggedUsers.remove(position);
            taggedUserAdapter.setTaggedUsers(taggedUsers);
            renderBadges();
        });

        // Event: Back button and Done button
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnDone.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("TAGGED_USERS", taggedUsers);
            setResult(RESULT_OK, result);
            finish();
        });

        binding.ivMainImage.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                currentTapX = event.getX() / v.getWidth();
                currentTapY = event.getY() / v.getHeight();
                openSearchFragment();
            }
            return true;
        });
    }

    private void openSearchFragment() {
        binding.fragmentContainer.setVisibility(View.VISIBLE);
        SearchUserTagFragment fragment = new SearchUserTagFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void closeSearchFragment() {
        binding.fragmentContainer.setVisibility(View.GONE);
    }

    public void onUserSelected(String userId, String username, String fullName, String avatarUrl) {
        taggedUsers.add(new SuggestedTag(userId, username, fullName, avatarUrl, (double) currentTapX, (double) currentTapY));
        closeSearchFragment();
        renderBadges();
        taggedUserAdapter.setTaggedUsers(taggedUsers);
    }

    private void renderBadges() {
        binding.flBadgesOverlay.removeAllViews();
        for (SuggestedTag user : taggedUsers) {
            // Inflate UI
            TextView badge = (TextView) getLayoutInflater().inflate(
                    R.layout.item_tag_user_badge,
                    binding.flBadgesOverlay,
                    false
            );
            badge.setText(user.getUsername());

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

            // Calculate position based on user.getPosition() and container size
            binding.flBadgesOverlay.post(() -> {
                int containerWidth = binding.flBadgesOverlay.getWidth();
                int containerHeight = binding.flBadgesOverlay.getHeight();

                float tapX = (float) (user.getPosition().getX() * containerWidth);
                float tapY = (float) (user.getPosition().getY() * containerHeight);

                badge.setX(tapX - (badge.getWidth() / 2f));
                badge.setY(tapY - (badge.getHeight() / 2f));

                if (badge.getX() < 0) badge.setX(0);
                if (badge.getX() + badge.getWidth() > containerWidth) {
                    badge.setX(containerWidth - badge.getWidth());
                }
                if (badge.getY() < 0) badge.setY(0);
                if (badge.getY() + badge.getHeight() > containerHeight) {
                    badge.setY(containerHeight - badge.getHeight());
                }
            });

            binding.flBadgesOverlay.addView(badge, params);
        }
    }
}