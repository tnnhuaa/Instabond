package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;

public class SearchActivity extends AppCompatActivity {

    private static final String NEW_LIKE_AVATAR =
            "https://www.figma.com/api/mcp/asset/fa36e179-3b7e-468e-82ab-1f455121149a";
    private static final String NEW_LIKE_PREVIEW =
            "https://www.figma.com/api/mcp/asset/fa734d94-c880-4d08-acc0-909ddc31ca11";
    private static final String NEW_FOLLOW_AVATAR =
            "https://www.figma.com/api/mcp/asset/c2c7a2b9-6188-426f-b7c1-9c64d9580810";
    private static final String TODAY_COMMENT_AVATAR =
            "https://www.figma.com/api/mcp/asset/d796641f-3100-4c8e-9c34-d50c20d18016";
    private static final String TODAY_COMMENT_PREVIEW =
            "https://www.figma.com/api/mcp/asset/dfc9e369-09db-45f4-a9fa-b8cff2fc5b26";
    private static final String TODAY_LIKE_AVATAR_ONE =
            "https://www.figma.com/api/mcp/asset/905cf66c-eb33-4e0a-b8eb-cf9650db7f81";
    private static final String TODAY_LIKE_AVATAR_TWO =
            "https://www.figma.com/api/mcp/asset/c5b15415-470c-4744-90fe-7b8ed08e1eb7";
    private static final String WEEK_AVATAR =
            "https://www.figma.com/api/mcp/asset/d4076afe-20f5-41b9-a29a-540a61113d40";
    private static final String WEEK_GALLERY_ONE =
            "https://www.figma.com/api/mcp/asset/4c020598-4f6b-4d8d-a617-84ee0123c4f3";
    private static final String WEEK_GALLERY_TWO =
            "https://www.figma.com/api/mcp/asset/f452447c-130a-4887-af4a-fd9558d2aead";
    private static final String WEEK_GALLERY_THREE =
            "https://www.figma.com/api/mcp/asset/3d0e9edd-d768-4c71-a257-44ca74046886";
    private static final String WEEK_TAG_AVATAR =
            "https://www.figma.com/api/mcp/asset/a190988e-4c13-4058-aa9d-7d29a25dba55";
    private static final String WEEK_TAG_PREVIEW =
            "https://www.figma.com/api/mcp/asset/4bab3e16-8d0e-4eff-b9d1-c87f089ba969";

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);
        bindStaticCopy();
        bindImages();
        bindActions();
    }

    private void bindStaticCopy() {
        setStyledText(binding.tvNewLikeMessage, "marcus_v", " liked your photo");
        binding.tvNewLikeTime.setText("2m ago");

        setStyledText(binding.tvNewFollowMessage, "sara.ink", " started following\nyou");
        binding.tvNewFollowTime.setText("15m ago");

        setStyledText(binding.tvTodayCommentTitle, "julian_art", " commented:");
        binding.tvTodayCommentBody.setText("\"This edit is fire!\"");
        binding.tvTodayCommentTime.setText("4h ago");

        setStyledText(binding.tvTodayLikeTitle, "clara_d and 12 others", " liked");
        binding.tvTodayLikeBody.setText("your story");
        binding.tvTodayLikeTime.setText("8h ago");

        binding.tvWeekProfileName.setText("elena_vision");
        binding.tvWeekProfileMeta.setText("Followed by julian_art + 4 others");

        setStyledText(binding.tvWeekTagTitle, "maya_pixels", " tagged you in a\nphoto");
        binding.tvWeekTagTime.setText("3d ago");
    }

    private void bindImages() {
        loadImage(binding.ivNewLikeAvatar, NEW_LIKE_AVATAR);
        loadImage(binding.ivNewLikePreview, NEW_LIKE_PREVIEW);
        loadImage(binding.ivNewFollowAvatar, NEW_FOLLOW_AVATAR);
        loadImage(binding.ivTodayCommentAvatar, TODAY_COMMENT_AVATAR);
        loadImage(binding.ivTodayCommentPreview, TODAY_COMMENT_PREVIEW);
        loadImage(binding.ivTodayLikeAvatarOne, TODAY_LIKE_AVATAR_ONE);
        loadImage(binding.ivTodayLikeAvatarTwo, TODAY_LIKE_AVATAR_TWO);
        loadImage(binding.ivWeekAvatar, WEEK_AVATAR);
        loadImage(binding.ivWeekGalleryOne, WEEK_GALLERY_ONE);
        loadImage(binding.ivWeekGalleryTwo, WEEK_GALLERY_TWO);
        loadImage(binding.ivWeekGalleryThree, WEEK_GALLERY_THREE);
        loadImage(binding.ivWeekTagAvatar, WEEK_TAG_AVATAR);
        loadImage(binding.ivWeekTagPreview, WEEK_TAG_PREVIEW);
    }

    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());

        binding.btnFollow.setOnClickListener(v -> {
            binding.btnFollow.setText(getString(R.string.search_action_following));
            binding.btnFollow.setAlpha(0.82f);
        });

        binding.btnFollowBack.setOnClickListener(v -> {
            binding.btnFollowBack.setText(getString(R.string.search_action_following));
            binding.btnFollowBack.setAlpha(0.82f);
        });

        binding.btnDismissSuggestion.setOnClickListener(v -> binding.cardWeekSuggestion.setVisibility(View.GONE));
    }

    private void loadImage(ImageView target, String url) {
        Glide.with(this)
                .load(url)
                .centerCrop()
                .into(target);
    }

    private void setStyledText(TextView view, String boldPart, String regularPart) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int start = 0;
        builder.append(boldPart);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(regularPart);
        view.setText(builder);
    }
}
