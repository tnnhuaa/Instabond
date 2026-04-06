package com.example.instabond_fe.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private int deleteAreaWidthPx;
    private int deleteIconSizePx;
    private Drawable deleteIconDrawable;

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
        setupTaggedListDeleteX();

        taggedUserAdapter.setTaggedUsers(taggedUsers);

        taggedUserAdapter.setOnRemoveClickListener((tag, position) -> removeTaggedUserAt(position));

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

    private void setupTaggedListDeleteX() {
        deleteAreaWidthPx = dpToPx(44);
        deleteIconSizePx = dpToPx(18);
        deleteIconDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_close_small);

        binding.rvTaggedList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.right = deleteAreaWidthPx;
            }

            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                if (deleteIconDrawable == null) {
                    return;
                }

                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = parent.getChildAt(i);
                    int deleteLeft = child.getRight();
                    int deleteRight = Math.min(deleteLeft + deleteAreaWidthPx, parent.getWidth() - parent.getPaddingRight());
                    int centerX = deleteLeft + ((deleteRight - deleteLeft) / 2);
                    int centerY = child.getTop() + (child.getHeight() / 2);

                    int iconHalf = deleteIconSizePx / 2;
                    deleteIconDrawable.setBounds(
                            centerX - iconHalf,
                            centerY - iconHalf,
                            centerX + iconHalf,
                            centerY + iconHalf
                    );
                    deleteIconDrawable.draw(c);
                }
            }
        });

        binding.rvTaggedList.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (e.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                View touchedChild = null;
                int childCount = rv.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = rv.getChildAt(i);
                    if (e.getY() < child.getTop() || e.getY() > child.getBottom()) {
                        continue;
                    }
                    float deleteStartX = child.getRight();
                    float deleteEndX = Math.min(child.getRight() + deleteAreaWidthPx, rv.getWidth() - rv.getPaddingRight());
                    if (e.getX() >= deleteStartX && e.getX() <= deleteEndX) {
                        touchedChild = child;
                        break;
                    }
                }

                if (touchedChild == null) {
                    return false;
                }

                int position = rv.getChildAdapterPosition(touchedChild);
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }

                removeTaggedUserAt(position);
                return true;
            }
        });
    }

    private void removeTaggedUserAt(int position) {
        if (position < 0 || position >= taggedUsers.size()) {
            return;
        }
        taggedUsers.remove(position);
        taggedUserAdapter.setTaggedUsers(taggedUsers);
        renderBadges();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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