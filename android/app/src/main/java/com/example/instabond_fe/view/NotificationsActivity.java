package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityNotificationsBinding;
import com.example.instabond_fe.databinding.ItemNotificationBinding;
import com.example.instabond_fe.model.Notification;
import com.example.instabond_fe.model.NotificationPageResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.TimeUtils;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsActivity extends AppCompatActivity {
    private static final String TAG = "NotificationsActivity";
    private static final String EXTRA_ENTRY_TYPE = "entryType";
    private static final String EXTRA_FALLBACK_USER_ID = "fallbackUserId";
    private ActivityNotificationsBinding binding;
    private ApiService apiService;
    private NotificationAdapter adapter;
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
        getWindow().setStatusBarColor(Color.WHITE);

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
    private final WebSocketManager.NotificationListener notificationListener = payload -> {
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
    };
    private void setupRecyclerView() {
        adapter = new NotificationAdapter(notificationList, notification -> {
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
        binding.swipeRefresh.setOnRefreshListener(() -> loadNotifications(0));
    }
    private void loadNotifications(int page) {
        if (isLoading) return;
        isLoading = true;
        if (page == 0) {
            binding.swipeRefresh.setRefreshing(true);
        }

        apiService.getNotifications(page, 20).enqueue(new Callback<NotificationPageResponse>() {
            @Override
            public void onResponse(@NonNull Call<NotificationPageResponse> call, @NonNull Response<NotificationPageResponse> response) {
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    NotificationPageResponse pageData = response.body();
                    if (page == 0) {
                        notificationList.clear();
                        adapter.notifyDataSetChanged();
                    }

                    for (Notification n : pageData.getData()) {
                        addOrUpdateNotification(n);
                    }

                    currentPage = pageData.getPage();
                    hasNextPage = pageData.isHasNext();
                    updateEmptyState();
                } else {
                    Toast.makeText(NotificationsActivity.this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<NotificationPageResponse> call, @NonNull Throwable t) {
                isLoading = false;
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(NotificationsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private synchronized void addOrUpdateNotification(Notification notification) {
        int existingIndex = -1;
        for (int i = 0; i < notificationList.size(); i++) {
            if (notificationList.get(i).getId().equals(notification.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            notificationList.set(existingIndex, notification);
            adapter.notifyItemChanged(existingIndex);
        } else {
            int insertAt = 0;
            boolean inserted = false;
            for (int i = 0; i < notificationList.size(); i++) {
                if (notification.getCreatedAt().compareTo(notificationList.get(i).getCreatedAt()) >= 0) {
                    insertAt = i;
                    inserted = true;
                    break;
                }
            }

            if (!inserted) {
                insertAt = notificationList.size();
            }
            notificationList.add(insertAt, notification);
            adapter.notifyItemInserted(insertAt);

            if (insertAt == 0) {
                binding.rvNotifications.smoothScrollToPosition(0);
            }
        }
    }
    private void updateEmptyState() {
        binding.tvEmpty.setVisibility(notificationList.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void markAsRead(Notification notification) {
        if (notification.isRead()) return;

        notification.setRead(true);
        addOrUpdateNotification(notification);

        apiService.markNotificationAsRead(notification.getId()).enqueue(new Callback<Notification>() {
            @Override
            public void onResponse(@NonNull Call<Notification> call, @NonNull Response<Notification> response) {
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
    private void setStyledText(TextView view, String boldPart, String regularPart) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int start = 0;
        builder.append(boldPart);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(regularPart);
        view.setText(builder);
    }
    class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private final List<Notification> items;
        private final OnNotificationClickListener listener;
        NotificationAdapter(List<Notification> items, OnNotificationClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Notification item = items.get(position);
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemNotificationBinding binding;
            ViewHolder(ItemNotificationBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
            void bind(Notification item, OnNotificationClickListener listener) {
                String content = item.getContent();
                if (content != null && content.contains(" ")) {
                    int firstSpace = content.indexOf(" ");
                    String sender = content.substring(0, firstSpace);
                    String action = content.substring(firstSpace);
                    setStyledText(binding.tvMessage, sender, action);
                } else {
                    binding.tvMessage.setText(content);
                }

                binding.tvTime.setText(TimeUtils.getRelativeTime(item.getCreatedAt()));
                if (item.isRead()) {
                    binding.getRoot().setBackgroundColor(Color.parseColor("#FFFFFF"));
                } else {
                    binding.getRoot().setBackgroundColor(Color.parseColor("#F2F2F2"));
                }

                String type = item.getType();

                if ("LIKE".equals(type)) {
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_heart);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.VISIBLE);

                    String postImageUrl = null;
                    if (item.getMetadata() != null && item.getMetadata().containsKey("post_image_url")) {
                        postImageUrl = item.getMetadata().get("post_image_url");
                    }

                    if (postImageUrl != null && !postImageUrl.isEmpty()) {
                        Glide.with(binding.getRoot().getContext())
                                .load(postImageUrl)
                                .placeholder(R.drawable.notification_preview_placeholder)
                                .error(R.drawable.notification_preview_placeholder)
                                .into(binding.ivPreview);
                    } else {
                        binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);
                    }
                } else if ("COMMENT".equals(type)) {
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_message_circle);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.VISIBLE);
                    binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);

                    binding.ivPreview.setVisibility(View.VISIBLE);

                    String postImageUrl = null;
                    if (item.getMetadata() != null && item.getMetadata().containsKey("post_image_url")) {
                        postImageUrl = item.getMetadata().get("post_image_url");
                    }

                    if (postImageUrl != null && !postImageUrl.isEmpty()) {
                        Glide.with(binding.getRoot().getContext())
                                .load(postImageUrl)
                                .placeholder(R.drawable.notification_preview_placeholder)
                                .error(R.drawable.notification_preview_placeholder)
                                .into(binding.ivPreview);
                    } else {
                        binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);
                    }
                } else if ("FOLLOW".equals(type)) {
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_person);
                    binding.largeChip.setVisibility(View.VISIBLE);
                    binding.ivLargeIcon.setImageResource(R.drawable.ic_user_plus);
                    binding.ivPreview.setVisibility(View.GONE);
                }
                else if ("LIKE_COMMENT".equals(type)) {
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_heart);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.VISIBLE);

                    String postImageUrl = null;
                    if (item.getMetadata() != null && item.getMetadata().containsKey("post_image_url")) {
                        postImageUrl = item.getMetadata().get("post_image_url");
                    }

                    if (postImageUrl != null && !postImageUrl.isEmpty()) {
                        Glide.with(binding.getRoot().getContext())
                                .load(postImageUrl)
                                .placeholder(R.drawable.notification_preview_placeholder)
                                .error(R.drawable.notification_preview_placeholder)
                                .into(binding.ivPreview);
                    } else {
                        binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);
                    }
                } else if ("REPLY_COMMENT".equals(type)){
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_message_circle);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.VISIBLE);
                    binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);

                    binding.ivPreview.setVisibility(View.VISIBLE);

                    String postImageUrl = null;
                    if (item.getMetadata() != null && item.getMetadata().containsKey("post_image_url")) {
                        postImageUrl = item.getMetadata().get("post_image_url");
                    }

                    if (postImageUrl != null && !postImageUrl.isEmpty()) {
                        Glide.with(binding.getRoot().getContext())
                                .load(postImageUrl)
                                .placeholder(R.drawable.notification_preview_placeholder)
                                .error(R.drawable.notification_preview_placeholder)
                                .into(binding.ivPreview);
                    } else {
                        binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);
                    }
                } else if ("TAG".equals(type)) {
                    binding.smallChip.setVisibility(View.VISIBLE);
                    binding.ivSmallIcon.setImageResource(R.drawable.ic_message_circle);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.VISIBLE);
                    binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);

                    binding.ivPreview.setVisibility(View.VISIBLE);

                    String postImageUrl = null;
                    if (item.getMetadata() != null && item.getMetadata().containsKey("post_image_url")) {
                        postImageUrl = item.getMetadata().get("post_image_url");
                    }

                    if (postImageUrl != null && !postImageUrl.isEmpty()) {
                        Glide.with(binding.getRoot().getContext())
                                .load(postImageUrl)
                                .placeholder(R.drawable.notification_preview_placeholder)
                                .error(R.drawable.notification_preview_placeholder)
                                .into(binding.ivPreview);
                    } else {
                        binding.ivPreview.setImageResource(R.drawable.notification_preview_placeholder);
                    }
                } else {
                    binding.smallChip.setVisibility(View.GONE);
                    binding.largeChip.setVisibility(View.GONE);
                    binding.ivPreview.setVisibility(View.GONE);
                }

                String avatarUrl = null;
                if (item.getMetadata() != null && item.getMetadata().containsKey("sender_image_url")) {
                    avatarUrl = item.getMetadata().get("sender_image_url");
                }

                AvatarLoader.load(binding.ivAvatar, avatarUrl);
                binding.getRoot().setOnClickListener(v -> listener.onNotificationClick(item));
            }
        }
    }
    interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

}
