package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityNotificationsBinding;
import com.example.instabond_fe.databinding.ItemNotificationSectionBinding;
import com.example.instabond_fe.databinding.ViewNotificationCardBinding;
import com.example.instabond_fe.model.Notification;
import com.example.instabond_fe.model.NotificationPageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.utils.ThemePreferenceManager;
import com.example.instabond_fe.utils.TimeUtils;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private static final String TAG = "NotificationsActivity";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long WEEK_MS = 7L * DAY_MS;

    private static final String EXTRA_ENTRY_TYPE = "entryType";
    private static final String EXTRA_FALLBACK_USER_ID = "fallbackUserId";
    private ActivityNotificationsBinding binding;
    private ApiService apiService;
    private NotificationSectionAdapter adapter;
    private final List<Notification> notificationList = new ArrayList<>();
    private WebSocketManager webSocketManager;
    private boolean isLoading = false;
    private boolean hasNextPage = true;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configureSystemBars();
        applyBrandGradient();

        apiService = ApiClient.getApiService(this);
        webSocketManager = WebSocketManager.getInstance(this);

        setupRecyclerView();
        setupSwipeRefresh();
        bindActions();

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.NOTIFICATIONS);

        loadNotifications(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webSocketManager.addNotificationListener(notificationListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webSocketManager.removeNotificationListener(notificationListener);
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!ThemePreferenceManager.isDarkModeEnabled(this));
    }

    private void applyBrandGradient() {
        binding.tvAppName.post(() -> {
            float width = binding.tvAppName.getPaint()
                    .measureText(binding.tvAppName.getText().toString());
            if (width <= 0f) {
                return;
            }
            int start = ContextCompat.getColor(this, R.color.theme_primary);
            int end = ContextCompat.getColor(this, R.color.theme_primary_container);
            Shader shader = new LinearGradient(0, 0, width, 0, start, end, Shader.TileMode.CLAMP);
            binding.tvAppName.getPaint().setShader(shader);
            binding.tvAppName.invalidate();
        });
    }

    private final WebSocketManager.NotificationListener notificationListener = payload ->
            runOnUiThread(() -> {
                try {
                    Notification notification = new Gson().fromJson(payload, Notification.class);
                    if (notification != null) {
                        addOrUpdateNotification(notification);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing websocket notification", e);
                }
            });

    private void setupRecyclerView() {
        adapter = new NotificationSectionAdapter(notification -> {
            markAsRead(notification);
            handleNotificationClick(notification);
        });
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);
        binding.rvNotifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1) && hasNextPage && !isLoading) {
                    loadNotifications(currentPage + 1);
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
                R.color.theme_primary,
                R.color.theme_primary_container
        );
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.notification_card_surface);
        binding.swipeRefresh.setOnRefreshListener(() -> loadNotifications(0));
    }

    private void loadNotifications(int page) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        if (page == 0) {
            binding.swipeRefresh.setRefreshing(true);
        }

        apiService.getNotifications(page, 20).enqueue(new Callback<NotificationPageResponse>() {
            @Override
            public void onResponse(@NonNull Call<NotificationPageResponse> call,
                                   @NonNull Response<NotificationPageResponse> response) {
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    NotificationPageResponse pageData = response.body();
                    if (page == 0) {
                        notificationList.clear();
                    }

                    for (Notification notification : pageData.getData()) {
                        upsertNotification(notification);
                    }

                    currentPage = pageData.getPage();
                    hasNextPage = pageData.isHasNext();
                    rebuildSections();
                } else {
                    Toast.makeText(
                            NotificationsActivity.this,
                            "Failed to load notifications",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<NotificationPageResponse> call,
                                  @NonNull Throwable t) {
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(
                        NotificationsActivity.this,
                        "Error: " + t.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private synchronized void addOrUpdateNotification(Notification notification) {
        upsertNotification(notification);
        rebuildSections();
    }

    private void rebuildSections() {
        adapter.submitSections(buildSections(notificationList));
        updateEmptyState();
    }

    private void updateEmptyState() {
        binding.tvEmpty.setVisibility(notificationList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void markAsRead(Notification notification) {
        if (notification.isRead()) {
            return;
        }

        notification.setRead(true);
        addOrUpdateNotification(notification);

        apiService.markNotificationAsRead(notification.getId()).enqueue(new Callback<Notification>() {
            @Override
            public void onResponse(@NonNull Call<Notification> call,
                                   @NonNull Response<Notification> response) {
            }

            @Override
            public void onFailure(@NonNull Call<Notification> call, @NonNull Throwable t) {
            }
        });
    }

    private void handleNotificationClick(Notification notification) {
        String type = notification.getType();

        if ("LIKE".equals(type) || "COMMENT".equals(type) || "REPLY_COMMENT".equals(type) || "LIKE_COMMENT".equals(type) || "TAG".equals(type)) {
            String postId = notification.getPostId();
            if (postId != null) {
                Intent intent = new Intent(this, CommentActivity.class);
                intent.putExtra("postId", postId);

                if ("TAG".equals(type)) {
                    intent.putExtra(EXTRA_ENTRY_TYPE, "TAG");
                    intent.putExtra(EXTRA_FALLBACK_USER_ID, notification.getSenderId());
                }

                if (!"LIKE".equals(type) && !"TAG".equals(type)) {
                    intent.putExtra("focusComment", true);
                }
                startActivity(intent);
            }
        } else if ("FOLLOW".equals(type)) {
            String content = notification.getContent();
            if (content != null && content.contains("sent you a follow request")) {
                Intent intent = new Intent(this, FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_MODE, "requests");
                SessionManager sessionManager = new SessionManager(this);
                intent.putExtra(FollowListActivity.EXTRA_USER_ID, sessionManager.getUserId());
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra("targetUserId", notification.getSenderId());
                intent.putExtra(ProfileActivity.EXTRA_PROFILE_NAV_CONTEXT, ProfileActivity.NAV_CONTEXT_SEARCH);
                startActivity(intent);
            }
        }
    }

    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
    }

    private void upsertNotification(Notification notification) {
        int existingIndex = -1;
        for (int i = 0; i < notificationList.size(); i++) {
            if (notificationList.get(i).getId().equals(notification.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            notificationList.set(existingIndex, notification);
            return;
        }

        int insertAt = notificationList.size();
        for (int i = 0; i < notificationList.size(); i++) {
            if (compareCreatedAt(notification, notificationList.get(i)) >= 0) {
                insertAt = i;
                break;
            }
        }
        notificationList.add(insertAt, notification);
    }

    private int compareCreatedAt(Notification left, Notification right) {
        Date leftDate = parseInstant(left.getCreatedAt());
        Date rightDate = parseInstant(right.getCreatedAt());
        long leftTime = leftDate != null ? leftDate.getTime() : 0L;
        long rightTime = rightDate != null ? rightDate.getTime() : 0L;
        return Long.compare(leftTime, rightTime);
    }

    private List<NotificationSection> buildSections(List<Notification> notifications) {
        List<Notification> newItems = new ArrayList<>();
        List<Notification> todayItems = new ArrayList<>();
        List<Notification> weekItems = new ArrayList<>();
        List<Notification> earlierItems = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Notification notification : notifications) {
            Date createdAt = parseInstant(notification.getCreatedAt());
            long age = createdAt == null ? Long.MAX_VALUE : Math.max(now - createdAt.getTime(), 0L);

            if (!notification.isRead()) {
                newItems.add(notification);
            } else if (age < DAY_MS) {
                todayItems.add(notification);
            } else if (age < WEEK_MS) {
                weekItems.add(notification);
            } else {
                earlierItems.add(notification);
            }
        }

        List<NotificationSection> sections = new ArrayList<>();
        appendSection(sections, getString(R.string.notification_section_new), true, false, newItems);
        appendSection(sections, getString(R.string.notification_section_today), false, true, todayItems);
        appendSection(sections, getString(R.string.notification_section_this_week), false, false, weekItems);
        appendSection(sections, getString(R.string.notification_section_earlier), false, false, earlierItems);
        return sections;
    }

    private void appendSection(List<NotificationSection> sections,
                               String title,
                               boolean showDot,
                               boolean grouped,
                               List<Notification> items) {
        if (items.isEmpty()) {
            return;
        }
        sections.add(new NotificationSection(title, showDot, grouped, new ArrayList<>(items)));
    }

    private Date parseInstant(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }

        try {
            String normalized = createdAt;
            if (normalized.contains(".")) {
                normalized = normalized.substring(0, normalized.indexOf('.'));
            }
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(normalized);
        } catch (ParseException e) {
            return null;
        }
    }

    private static class NotificationSection {
        final String title;
        final boolean showAccentDot;
        final boolean grouped;
        final List<Notification> items;

        NotificationSection(String title, boolean showAccentDot, boolean grouped, List<Notification> items) {
            this.title = title;
            this.showAccentDot = showAccentDot;
            this.grouped = grouped;
            this.items = items;
        }
    }

    private final class NotificationSectionAdapter
            extends RecyclerView.Adapter<NotificationSectionAdapter.SectionViewHolder> {

        private final List<NotificationSection> sections = new ArrayList<>();
        private final OnNotificationClickListener listener;

        NotificationSectionAdapter(OnNotificationClickListener listener) {
            this.listener = listener;
        }

        void submitSections(List<NotificationSection> newSections) {
            sections.clear();
            sections.addAll(newSections);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new SectionViewHolder(ItemNotificationSectionBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
            holder.bind(sections.get(position));
        }

        @Override
        public int getItemCount() {
            return sections.size();
        }

        final class SectionViewHolder extends RecyclerView.ViewHolder {
            private final ItemNotificationSectionBinding sectionBinding;

            SectionViewHolder(ItemNotificationSectionBinding sectionBinding) {
                super(sectionBinding.getRoot());
                this.sectionBinding = sectionBinding;
            }

            void bind(NotificationSection section) {
                sectionBinding.tvSectionTitle.setText(section.title);
                sectionBinding.tvSectionTitle.setTextColor(ContextCompat.getColor(
                        sectionBinding.getRoot().getContext(),
                        section.showAccentDot
                                ? R.color.notification_section_title
                                : R.color.notification_section_title_muted
                ));
                sectionBinding.viewSectionDot.setVisibility(
                        section.showAccentDot ? View.VISIBLE : View.GONE
                );
                sectionBinding.cardsContainer.setVisibility(section.grouped ? View.GONE : View.VISIBLE);
                sectionBinding.cardGroupShell.setVisibility(section.grouped ? View.VISIBLE : View.GONE);

                LinearLayout targetContainer = section.grouped
                        ? sectionBinding.groupCardsContainer
                        : sectionBinding.cardsContainer;
                targetContainer.removeAllViews();

                for (int i = 0; i < section.items.size(); i++) {
                    Notification item = section.items.get(i);
                    ViewNotificationCardBinding cardBinding = ViewNotificationCardBinding.inflate(
                            LayoutInflater.from(targetContainer.getContext()),
                            targetContainer,
                            false
                    );
                    bindCard(cardBinding, item, section.grouped);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    params.bottomMargin = i == section.items.size() - 1
                            ? 0
                            : dp(section.grouped ? 4 : 20);
                    cardBinding.getRoot().setLayoutParams(params);
                    targetContainer.addView(cardBinding.getRoot());
                }
            }

            private void bindCard(ViewNotificationCardBinding cardBinding,
                                  Notification item,
                                  boolean grouped) {
                MaterialCardView card = cardBinding.cardRoot;
                card.setCardElevation(grouped ? 0f : dp(6));
                card.setCardBackgroundColor(ContextCompat.getColor(
                        cardBinding.getRoot().getContext(),
                        R.color.notification_card_surface
                ));

                AvatarLoader.load(cardBinding.ivAvatar, getMetadataValue(item, "sender_image_url"));
                bindMessage(cardBinding, item);

                String type = item.getType();
                boolean followAction = "FOLLOW".equals(type)
                        && !containsText(item.getContent(), "follow request");
                String previewUrl = getMetadataValue(item, "post_image_url");

                cardBinding.smallChip.setVisibility(shouldShowAvatarChip(type) ? View.VISIBLE : View.GONE);
                cardBinding.ivSmallIcon.setImageResource(resolveChipIcon(type));

                cardBinding.btnFollow.setVisibility(followAction ? View.VISIBLE : View.GONE);
                cardBinding.cardPreview.setVisibility(
                        !followAction && hasText(previewUrl) ? View.VISIBLE : View.GONE
                );
                cardBinding.placeholderPreview.setVisibility(
                        !followAction && !hasText(previewUrl) ? View.VISIBLE : View.GONE
                );

                if (hasText(previewUrl)) {
                    Glide.with(cardBinding.ivPreview)
                            .load(previewUrl)
                            .placeholder(R.drawable.notification_preview_placeholder)
                            .error(R.drawable.notification_preview_placeholder)
                            .into(cardBinding.ivPreview);
                } else {
                    Glide.with(cardBinding.ivPreview).clear(cardBinding.ivPreview);
                }

                cardBinding.ivPlaceholderIcon.setImageResource(resolvePlaceholderIcon(item));
                cardBinding.btnFollow.setOnClickListener(v -> listener.onNotificationClick(item));
                cardBinding.getRoot().setOnClickListener(v -> listener.onNotificationClick(item));
            }

            private void bindMessage(ViewNotificationCardBinding cardBinding, Notification item) {
                String content = item.getContent() == null ? "" : item.getContent().trim();
                int firstSpace = content.indexOf(' ');
                if (firstSpace <= 0) {
                    cardBinding.tvMessage.setText(content);
                } else {
                    String actor = content.substring(0, firstSpace);
                    String remainder = content.substring(firstSpace);
                    SpannableStringBuilder builder = new SpannableStringBuilder(actor + remainder);
                    builder.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            0,
                            actor.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    builder.setSpan(
                            new ForegroundColorSpan(ContextCompat.getColor(
                                    cardBinding.getRoot().getContext(),
                                    R.color.notification_primary_text
                            )),
                            0,
                            actor.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    cardBinding.tvMessage.setText(builder);
                }
                cardBinding.tvTime.setText(TimeUtils.getConversationTimeLabel(item.getCreatedAt()));
            }

            private boolean shouldShowAvatarChip(String type) {
                return "LIKE".equals(type)
                        || "COMMENT".equals(type)
                        || "LIKE_COMMENT".equals(type)
                        || "REPLY_COMMENT".equals(type);
            }

            private int resolveChipIcon(String type) {
                if ("COMMENT".equals(type) || "REPLY_COMMENT".equals(type)) {
                    return R.drawable.ic_message_circle;
                }
                return R.drawable.ic_heart;
            }

            private int resolvePlaceholderIcon(Notification item) {
                String type = item.getType();
                String content = item.getContent() == null ? "" : item.getContent().toLowerCase(Locale.getDefault());

                if ("FOLLOW".equals(type)) {
                    return R.drawable.ic_user_plus;
                }
                if (content.contains("tagged")) {
                    return R.drawable.ic_tag;
                }
                if (content.contains("story")) {
                    return R.drawable.ic_story_sparkles;
                }
                if ("COMMENT".equals(type) || "REPLY_COMMENT".equals(type)) {
                    return R.drawable.ic_message_circle;
                }
                return R.drawable.ic_heart;
            }

            private String getMetadataValue(Notification item, String key) {
                Map<String, String> metadata = item.getMetadata();
                return metadata != null ? metadata.get(key) : null;
            }

            private boolean hasText(String value) {
                return value != null && !value.trim().isEmpty();
            }

            private boolean containsText(String value, String query) {
                return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
            }

            private int dp(int value) {
                float density = sectionBinding.getRoot().getResources().getDisplayMetrics().density;
                return Math.round(value * density);
            }
        }
    }

    private interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }
}
