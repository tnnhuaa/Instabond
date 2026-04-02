package com.example.instabond_fe.view;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityStoryViewerBinding;
import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.StoryItem;
import com.example.instabond_fe.model.StoryResponse;
import com.example.instabond_fe.model.StoryViewerResponse;
import com.example.instabond_fe.model.StoryViewersResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.RichMessageUtils;
import com.example.instabond_fe.utils.TimeUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StoryViewerActivity extends AppCompatActivity {
    public static final String EXTRA_STORIES = "extra_stories";
    public static final String EXTRA_STORY_INDEX = "extra_story_index";

    private static final int STORY_DURATION_MS = 5000;
    private static final int PROGRESS_TICK_MS = 50;

    private ActivityStoryViewerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ProgressBar> progressBars = new ArrayList<>();
    private final Set<String> trackedViewedStoryIds = new HashSet<>();

    private ApiService apiService;
    private ChatRepository chatRepository;
    private SessionManager sessionManager;
    private ArrayList<StoryItem> stories = new ArrayList<>();
    private int currentIndex = 0;
    private long progressStartedAt = 0L;
    private boolean likeRequestInFlight;
    private boolean replyRequestInFlight;
    private List<StoryViewerResponse> currentViewers = new ArrayList<>();

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

        apiService = ApiClient.getApiService(this);
        chatRepository = ChatRepository.getInstance(this);
        sessionManager = new SessionManager(this);

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

        bindActions();
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

    private void bindActions() {
        binding.btnCloseStory.setOnClickListener(v -> finish());
        binding.storyTapLeft.setOnClickListener(v -> showPreviousStory());
        binding.storyTapRight.setOnClickListener(v -> showNextStoryOrFinish());
        binding.btnStoryMore.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_view_more_soon, Toast.LENGTH_SHORT).show());
        binding.btnStoryLike.setOnClickListener(v -> toggleLikeCurrentStory());
        binding.btnStorySend.setOnClickListener(v -> sendStoryReply());
        binding.tvStoryViewersSummary.setOnClickListener(v -> showViewersBottomSheet());
        binding.etStoryReply.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                stopProgress();
            } else {
                startProgress();
            }
        });
        binding.etStoryReply.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSend = actionId == EditorInfo.IME_ACTION_SEND;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isSend || isEnter) {
                sendStoryReply();
                return true;
            }
            return false;
        });
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

        AvatarLoader.load(binding.ivStoryAvatar, normalizeUrl(story.getAvatarUrl()));

        binding.tvStoryUsername.setText(story.getUsername());
        binding.tvStoryMeta.setText(formatStoryMeta(story.getCreatedAt()));
        binding.etStoryReply.setText("");
        currentViewers = new ArrayList<>();

        updateLikeButton(story);
        updateOwnerUi(story);
        resetProgressBars();
        startProgress();

        if (isOwnStory(story)) {
            loadStoryViewers(story);
        } else {
            markStoryViewed(story);
        }
    }

    private void updateOwnerUi(StoryItem story) {
        boolean ownStory = isOwnStory(story);
        binding.storyReplyBar.setVisibility(ownStory ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.tvStoryViewersSummary.setVisibility(ownStory ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.btnStoryLike.setEnabled(!ownStory && !likeRequestInFlight);
        updateViewersSummary(story.getViewerCount());
    }

    private void updateLikeButton(StoryItem story) {
        int tint = ContextCompat.getColor(this,
                story.isLikedByMe() ? R.color.login_bg_start : android.R.color.white);
        binding.btnStoryLike.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
        binding.btnStoryLike.setAlpha(likeRequestInFlight ? 0.55f : 1f);
    }

    private void updateViewersSummary(int viewerCount) {
        binding.tvStoryViewersSummary.setText(getString(R.string.story_view_viewers_summary, viewerCount));
    }

    private void markStoryViewed(StoryItem story) {
        String storyId = story.getId();
        if (RichMessageUtils.isBlank(storyId) || trackedViewedStoryIds.contains(storyId)) {
            return;
        }

        trackedViewedStoryIds.add(storyId);
        apiService.markStoryViewed(storyId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<StoryResponse> call, Response<StoryResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                applyStoryState(story, response.body());
            }

            @Override
            public void onFailure(Call<StoryResponse> call, Throwable t) {
            }
        });
    }

    private void toggleLikeCurrentStory() {
        StoryItem story = stories.get(currentIndex);
        if (isOwnStory(story) || likeRequestInFlight || RichMessageUtils.isBlank(story.getId())) {
            return;
        }

        likeRequestInFlight = true;
        updateLikeButton(story);

        Call<StoryResponse> request = story.isLikedByMe()
                ? apiService.unlikeStory(story.getId())
                : apiService.likeStory(story.getId());
        request.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<StoryResponse> call, Response<StoryResponse> response) {
                likeRequestInFlight = false;
                if (!response.isSuccessful() || response.body() == null) {
                    refreshCurrentStoryButtons();
                    Toast.makeText(StoryViewerActivity.this, R.string.story_view_like_failed, Toast.LENGTH_SHORT).show();
                    return;
                }

                applyStoryState(story, response.body());
                refreshCurrentStoryButtons();
            }

            @Override
            public void onFailure(Call<StoryResponse> call, Throwable t) {
                likeRequestInFlight = false;
                refreshCurrentStoryButtons();
                Toast.makeText(StoryViewerActivity.this, R.string.story_view_like_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendStoryReply() {
        StoryItem story = stories.get(currentIndex);
        if (isOwnStory(story) || replyRequestInFlight) {
            if (isOwnStory(story)) {
                Toast.makeText(this, R.string.story_view_reply_own_story, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String replyText = binding.etStoryReply.getText() == null ? "" : binding.etStoryReply.getText().toString().trim();
        String authorId = story.getAuthorId() == null ? "" : story.getAuthorId().trim();
        if (RichMessageUtils.isBlank(authorId)) {
            Toast.makeText(this, R.string.story_view_reply_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String currentUserId = sessionManager.getUserId();
        if (currentUserId != null && currentUserId.trim().equals(authorId)) {
            Toast.makeText(this, R.string.story_view_reply_own_story, Toast.LENGTH_SHORT).show();
            return;
        }

        replyRequestInFlight = true;
        setReplySendingState(true);

        apiService.getOrCreateDirectConversation(authorId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<Conversation> call, Response<Conversation> response) {
                if (!response.isSuccessful() || response.body() == null || RichMessageUtils.isBlank(response.body().getId())) {
                    showReplyError(extractErrorMessage(response, R.string.story_view_reply_failed));
                    finishReplyRequest(false);
                    return;
                }

                String payload = RichMessageUtils.buildStoryReplyPayload(story, replyText);
                ChatMessageRequest request = new ChatMessageRequest(response.body().getId(), payload, "story_reply");
                apiService.sendTextMessage(request).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<ChatMessageResponse> call, Response<ChatMessageResponse> sendResponse) {
                        if (sendResponse.isSuccessful()) {
                            finishReplyRequest(true);
                            return;
                        }
                        String errorMessage = extractErrorMessage(sendResponse, R.string.story_view_reply_failed);
                        if (!sendReplyViaSocketFallback(request)) {
                            showReplyError(errorMessage);
                            finishReplyRequest(false);
                        }
                    }

                    @Override
                    public void onFailure(Call<ChatMessageResponse> call, Throwable t) {
                        if (!sendReplyViaSocketFallback(request)) {
                            showReplyError(t == null ? getString(R.string.story_view_reply_failed) : t.getMessage());
                            finishReplyRequest(false);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<Conversation> call, Throwable t) {
                showReplyError(t == null ? getString(R.string.story_view_reply_failed) : t.getMessage());
                finishReplyRequest(false);
            }
        });
    }

    private void finishReplyRequest(boolean success) {
        replyRequestInFlight = false;
        runOnUiThread(() -> {
            setReplySendingState(false);
            if (success) {
                binding.etStoryReply.setText("");
                binding.etStoryReply.clearFocus();
                Toast.makeText(this, R.string.story_view_reply_sent, Toast.LENGTH_SHORT).show();
                startProgress();
            } else {
                Toast.makeText(this, R.string.story_view_reply_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean sendReplyViaSocketFallback(ChatMessageRequest request) {
        try {
            chatRepository.connectRealtime();
            chatRepository.subscribeGlobalChannels();
            chatRepository.sendRealtimeMessage(request);
            finishReplyRequest(true);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void showReplyError(String message) {
        runOnUiThread(() -> Toast.makeText(
                this,
                RichMessageUtils.isBlank(message) ? getString(R.string.story_view_reply_failed) : message,
                Toast.LENGTH_SHORT
        ).show());
    }

    private String extractErrorMessage(Response<?> response, int fallbackResId) {
        if (response == null) {
            return getString(fallbackResId);
        }

        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw != null && !raw.trim().isEmpty()) {
                    JsonObject obj = new JsonParser().parse(raw).getAsJsonObject();
                    if (obj.has("message") && !obj.get("message").isJsonNull()) {
                        String message = obj.get("message").getAsString();
                        if (!RichMessageUtils.isBlank(message)) {
                            return message;
                        }
                    }
                }
            }
        } catch (IOException | IllegalStateException ignored) {
        }

        return getString(fallbackResId) + " (HTTP " + response.code() + ")";
    }

    private void setReplySendingState(boolean sending) {
        binding.btnStorySend.setEnabled(!sending);
        binding.btnStorySend.setAlpha(sending ? 0.55f : 1f);
        binding.etStoryReply.setEnabled(!sending);
    }

    private void loadStoryViewers(StoryItem story) {
        if (RichMessageUtils.isBlank(story.getId())) {
            currentViewers = new ArrayList<>();
            updateViewersSummary(0);
            return;
        }

        apiService.getStoryViewers(story.getId()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<StoryViewersResponse> call, Response<StoryViewersResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                StoryViewersResponse body = response.body();
                story.setViewerCount(body.getViewerCount());
                if (isCurrentStory(story.getId())) {
                    currentViewers = body.getViewers() == null ? new ArrayList<>() : new ArrayList<>(body.getViewers());
                    updateViewersSummary(body.getViewerCount());
                }
            }

            @Override
            public void onFailure(Call<StoryViewersResponse> call, Throwable t) {
            }
        });
    }

    private void showViewersBottomSheet() {
        StoryItem story = stories.get(currentIndex);
        if (!isOwnStory(story)) {
            return;
        }

        if (!RichMessageUtils.isBlank(story.getId())) {
            apiService.getStoryViewers(story.getId()).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<StoryViewersResponse> call, Response<StoryViewersResponse> response) {
                    if (response.isSuccessful() && response.body() != null && isCurrentStory(story.getId())) {
                        StoryViewersResponse body = response.body();
                        currentViewers = body.getViewers() == null ? new ArrayList<>() : new ArrayList<>(body.getViewers());
                        story.setViewerCount(body.getViewerCount());
                        updateViewersSummary(body.getViewerCount());
                    }
                    openViewersSheet();
                }

                @Override
                public void onFailure(Call<StoryViewersResponse> call, Throwable t) {
                    openViewersSheet();
                }
            });
            return;
        }

        openViewersSheet();
    }

    private void openViewersSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = LayoutInflater.from(this).inflate(R.layout.layout_story_viewers_sheet, null);
        dialog.setContentView(view);

        TextView countView = view.findViewById(R.id.tv_story_viewers_count);
        TextView emptyView = view.findViewById(R.id.tv_story_viewers_empty);
        androidx.recyclerview.widget.RecyclerView recyclerView = view.findViewById(R.id.rv_story_viewers);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        StoryViewersAdapter adapter = new StoryViewersAdapter();
        recyclerView.setAdapter(adapter);
        adapter.submitList(currentViewers);

        countView.setText(getResources().getQuantityString(
                R.plurals.story_view_viewers_count,
                currentViewers.size(),
                currentViewers.size()));
        emptyView.setVisibility(currentViewers.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        recyclerView.setVisibility(currentViewers.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

        dialog.setOnShowListener(ignored -> stopProgress());
        dialog.setOnDismissListener(ignored -> startProgress());
        dialog.show();
    }

    private void applyStoryState(StoryItem story, StoryResponse response) {
        if (story == null || response == null) {
            return;
        }
        story.setViewedByMe(response.isViewedByMe());
        story.setLikedByMe(response.isLikedByMe());
        story.setViewerCount(response.getViewerCount());
        if (isCurrentStory(story.getId())) {
            updateLikeButton(story);
            updateViewersSummary(story.getViewerCount());
        }
    }

    private boolean isOwnStory(StoryItem story) {
        String currentUserId = sessionManager.getUserId();
        return story != null
                && currentUserId != null
                && currentUserId.equals(story.getAuthorId());
    }

    private boolean isCurrentStory(String storyId) {
        if (stories.isEmpty() || currentIndex < 0 || currentIndex >= stories.size()) {
            return false;
        }
        String currentStoryId = stories.get(currentIndex).getId();
        return storyId != null && storyId.equals(currentStoryId);
    }

    private void refreshCurrentStoryButtons() {
        if (stories.isEmpty() || currentIndex < 0 || currentIndex >= stories.size()) {
            return;
        }
        StoryItem currentStory = stories.get(currentIndex);
        updateOwnerUi(currentStory);
        updateLikeButton(currentStory);
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
