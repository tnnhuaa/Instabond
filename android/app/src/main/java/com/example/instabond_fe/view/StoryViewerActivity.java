package com.example.instabond_fe.view;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityStoryViewerBinding;
import com.example.instabond_fe.model.StoryItem;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class StoryViewerActivity extends AppCompatActivity {
    public static final String EXTRA_STORIES = "extra_stories";
    public static final String EXTRA_STORY_INDEX = "extra_story_index";

    private static final int STORY_DURATION_MS = 5000;
    private static final int PROGRESS_TICK_MS = 50;

    private ActivityStoryViewerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ProgressBar> progressBars = new ArrayList<>();

    private ArrayList<StoryItem> stories = new ArrayList<>();
    private int currentIndex = 0;
    private long progressStartedAt = 0L;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (stories.isEmpty() || currentIndex >= stories.size()) {
                return;
            }

            long elapsed = System.currentTimeMillis() - progressStartedAt;
            int progress = (int) Math.min(1000, (elapsed * 1000L) / STORY_DURATION_MS);
            progressBars.get(currentIndex).setProgress(progress);

            if (progress >= 1000) {
                showNextStoryOrFinish();
                return;
            }
            handler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        progressBars.add(binding.progressStory1);
        progressBars.add(binding.progressStory2);
        progressBars.add(binding.progressStory3);
        progressBars.add(binding.progressStory4);
        progressBars.add(binding.progressStory5);

        stories = extractStories();
        currentIndex = Math.max(0, getIntent().getIntExtra(EXTRA_STORY_INDEX, 0));
        if (stories.isEmpty()) {
            finish();
            return;
        }
        if (currentIndex >= stories.size()) {
            currentIndex = stories.size() - 1;
        }

        binding.btnCloseStory.setOnClickListener(v -> finish());
        binding.storyTapLeft.setOnClickListener(v -> showPreviousStory());
        binding.storyTapRight.setOnClickListener(v -> showNextStoryOrFinish());
        binding.btnStoryMore.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_view_more_soon, Toast.LENGTH_SHORT).show());
        binding.btnStoryLike.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_view_react_soon, Toast.LENGTH_SHORT).show());
        binding.btnStorySend.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_view_reply_soon, Toast.LENGTH_SHORT).show());
        binding.etStoryReply.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_view_reply_soon, Toast.LENGTH_SHORT).show());

        renderCurrentStory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startProgress();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopProgress();
    }

    @SuppressWarnings("unchecked")
    private ArrayList<StoryItem> extractStories() {
        Object payload = getIntent().getSerializableExtra(EXTRA_STORIES);
        if (payload instanceof ArrayList<?>) {
            return (ArrayList<StoryItem>) payload;
        }
        return new ArrayList<>();
    }

    private void renderCurrentStory() {
        StoryItem story = stories.get(currentIndex);

        Glide.with(this)
                .load(normalizeUrl(story.getMediaUrl()))
                .placeholder(R.drawable.create_post_preview_placeholder)
                .error(R.drawable.create_post_preview_placeholder)
                .into(binding.ivStoryMedia);

        Glide.with(this)
                .load(normalizeUrl(story.getAvatarUrl()))
                .placeholder(R.drawable.profile_placeholder_bg)
                .error(R.drawable.profile_placeholder_bg)
                .into(binding.ivStoryAvatar);

        binding.tvStoryUsername.setText(story.getUsername());
        binding.tvStoryMeta.setText(formatStoryMeta(story.getCreatedAt()));
        resetProgressBars();
        startProgress();
    }

    private void resetProgressBars() {
        for (int index = 0; index < progressBars.size(); index++) {
            ProgressBar progressBar = progressBars.get(index);
            progressBar.setProgress(index < currentIndex ? 1000 : 0);
            progressBar.setVisibility(index < stories.size() ? ProgressBar.VISIBLE : ProgressBar.GONE);
        }
    }

    private void startProgress() {
        stopProgress();
        progressStartedAt = System.currentTimeMillis();
        handler.post(progressRunnable);
    }

    private void stopProgress() {
        handler.removeCallbacks(progressRunnable);
    }

    private void showPreviousStory() {
        if (currentIndex <= 0) {
            return;
        }
        currentIndex--;
        renderCurrentStory();
    }

    private void showNextStoryOrFinish() {
        if (currentIndex >= stories.size() - 1) {
            finish();
            return;
        }
        currentIndex++;
        renderCurrentStory();
    }

    private String formatStoryMeta(String createdAt) {
        String label = TimeUtils.getConversationTimeLabel(createdAt);
        return label.replace(" ago", "");
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }

        Uri uri = Uri.parse(rawUrl);
        if (uri.getScheme() != null) {
            return rawUrl;
        }

        String baseUrl = ApiClient.getBaseUrl();
        if (rawUrl.startsWith("/")) {
            return baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1) + rawUrl
                    : baseUrl + rawUrl;
        }
        return baseUrl.endsWith("/") ? baseUrl + rawUrl : baseUrl + "/" + rawUrl;
    }
}
