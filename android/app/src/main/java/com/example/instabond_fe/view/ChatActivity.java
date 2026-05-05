package com.example.instabond_fe.view;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupWindow;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityChatBinding;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.viewmodel.ChatViewModel;
import com.example.instabond_fe.model.UserProfileResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private static final String EXTRA_CONVERSATION_ID = "CONVERSATION_ID";
    private static final String EXTRA_CONVERSATION_ID_FALLBACK = "conversationId";
    private static final String EXTRA_PARTNER_NAME = "PARTNER_NAME";
    private static final String EXTRA_PARTNER_NAME_FALLBACK = "partnerName";
    private static final String EXTRA_PARTNER_ID = "PARTNER_ID";
    private static final String EXTRA_PARTNER_ID_FALLBACK = "partnerId";
    private static final String EXTRA_PARTNER_EMAIL = "PARTNER_EMAIL";
    private static final String EXTRA_PARTNER_EMAIL_FALLBACK = "partnerEmail";
    private static final String EXTRA_PARTNER_AVATAR = "PARTNER_AVATAR";
    private static final String EXTRA_PARTNER_AVATAR_FALLBACK = "partnerAvatar";
    private static final String EXTRA_PARTNER_ONLINE = "PARTNER_ONLINE";
    private static final String EXTRA_PARTNER_ONLINE_FALLBACK = "partnerOnline";

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private ChatMessageAdapter messageAdapter;
    private ApiService apiService;
    private ActivityResultLauncher<String> pickImagesLauncher;
    private final List<Uri> pendingImageUris = new ArrayList<>();
    private boolean isImageUploading;

    private String conversationId;
    private String partnerName;
    private String partnerId;
    private String partnerEmail;
    private String partnerAvatar;
    private boolean partnerOnline;
    private int partnerStreakCount;
    private boolean partnerHasFiredStreak;
    private final Runnable refreshPartnerConversationMetaRunnable = this::fetchPartnerConversationMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        apiService = ApiClient.getApiService(this);
        registerLaunchers();

        readIntent();

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        messageAdapter = new ChatMessageAdapter(viewModel.getCurrentUserId());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(messageAdapter);

        renderPartnerAvatar();
        bindObservers();
        bindActions();
        renderPartnerHeader(partnerOnline);
        renderImagePreview();
        updateSendButtonState(hasTypedText());
        hydratePartnerProfileIfNeeded();
        fetchPartnerConversationMeta();

        viewModel.startChat(conversationId, partnerId, partnerEmail, partnerOnline);
        
        // Cache partner name
        if (partnerId != null && partnerName != null) {
            WebSocketManager.getInstance(this).cacheUser(partnerId, partnerName);
        }

        WebSocketManager.getInstance(this).setActiveConversationId(conversationId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.ensureRealtimeConnected();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebSocketManager.getInstance(this).setActiveConversationId(conversationId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebSocketManager.getInstance(this).setActiveConversationId(null);
    }

    @Override
    protected void onDestroy() {
        binding.topBar.removeCallbacks(refreshPartnerConversationMetaRunnable);
        viewModel.stopChat();
        WebSocketManager.getInstance(this).setActiveConversationId(null);
        super.onDestroy();
    }

    private void bindObservers() {
        viewModel.getMessagesLiveData().observe(this, messages -> runOnUiThread(() -> {
            messageAdapter.submitList(messages);
            if (messages != null && !messages.isEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size() - 1);
            }
            schedulePartnerConversationMetaRefresh();
        }));

        viewModel.getConnectionLiveData().observe(this, connected -> runOnUiThread(() ->
                binding.etMessage.setHint(Boolean.TRUE.equals(connected)
                        ? R.string.chat_type_message
                        : R.string.chat_reconnecting)));

        viewModel.getPartnerOnlineLiveData().observe(this, isOnline ->
                runOnUiThread(() -> renderPartnerHeader(Boolean.TRUE.equals(isOnline))));

        viewModel.getImageUploadingLiveData().observe(this, uploading -> runOnUiThread(() -> {
            isImageUploading = Boolean.TRUE.equals(uploading);
            binding.btnAddAttachment.setEnabled(!isImageUploading);
            binding.btnAddAttachment.setAlpha(isImageUploading ? 0.45f : 1f);
            binding.btnClearImagePreview.setEnabled(!isImageUploading);
            binding.btnClearImagePreview.setAlpha(isImageUploading ? 0.45f : 1f);
            updateSendButtonState(hasTypedText());
        }));

        viewModel.getErrorLiveData().observe(this, error -> runOnUiThread(() -> {
            if (error != null && !error.trim().isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void bindActions() {
        binding.btnBackChat.setOnClickListener(v -> finish());

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && !s.toString().trim().isEmpty();
                updateSendButtonState(hasText);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etMessage.getText() == null ? "" : binding.etMessage.getText().toString();
            if (isImageUploading) {
                return;
            }

            if (!text.trim().isEmpty()) {
                viewModel.sendTextMessage(text);
                binding.etMessage.setText("");
            }

            if (!pendingImageUris.isEmpty()) {
                viewModel.sendImageMessages(new ArrayList<>(pendingImageUris));
                clearPendingImagePreview();
            }
        });

        binding.btnAddAttachment.setOnClickListener(v -> openImagePicker());
        binding.btnCameraAction.setOnClickListener(this::showChatIconPicker);
        binding.btnClearImagePreview.setOnClickListener(v -> clearPendingImagePreview());
    }

    private void showChatIconPicker(View anchorView) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.bottom_sheet_chat_icons, null);

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setElevation(10f);

        setupIcon(popupWindow, popupView, R.id.tv_icon_heart, "\uD83D\uDE0D");
        setupIcon(popupWindow, popupView, R.id.tv_icon_laugh, "\uD83D\uDE02");
        setupIcon(popupWindow, popupView, R.id.tv_icon_love, "\u2764\uFE0F");
        setupIcon(popupWindow, popupView, R.id.tv_icon_fire, "\uD83D\uDD25");
        setupIcon(popupWindow, popupView, R.id.tv_icon_party, "\uD83C\uDF89");
        setupIcon(popupWindow, popupView, R.id.tv_icon_thumbs_up, "\uD83D\uDC4D");

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = popupView.getMeasuredHeight();

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.getHeight() - popupHeight - 20);
    }

    private void setupIcon(PopupWindow popupWindow, View popupView, int viewId, String icon) {
        android.widget.TextView tvIcon = popupView.findViewById(viewId);
        if (tvIcon != null) {
            tvIcon.setText(icon);

            tvIcon.setOnClickListener(v -> {
                appendChatIcon(icon);
                popupWindow.dismiss();
            });
        }
    }

    private void appendChatIcon(String icon) {
        if (icon == null || icon.trim().isEmpty()) {
            return;
        }
        String current = binding.etMessage.getText() == null ? "" : binding.etMessage.getText().toString();
        String spacer = current.trim().isEmpty() ? "" : " ";
        String updated = current + spacer + icon;
        binding.etMessage.setText(updated);
        binding.etMessage.setSelection(updated.length());
        updateSendButtonState(hasTypedText());
    }
    private void registerLaunchers() {
        pickImagesLauncher = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::onImagesPicked);
    }

    private void openImagePicker() {
        if (pickImagesLauncher == null) {
            Toast.makeText(this, R.string.chat_image_picker_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        pickImagesLauncher.launch("image/*");
    }

    private void onImagesPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }

        pendingImageUris.clear();
        for (Uri uri : uris) {
            if (uri != null) {
                pendingImageUris.add(uri);
            }
        }

        if (pendingImageUris.isEmpty()) {
            return;
        }

        renderImagePreview();
        updateSendButtonState(hasTypedText());
    }

    private void renderImagePreview() {
        if (pendingImageUris.isEmpty()) {
            binding.layoutImagePreview.setVisibility(View.GONE);
            clearPreviewSlot(binding.ivSelectedImagePreview1);
            clearPreviewSlot(binding.ivSelectedImagePreview2);
            clearPreviewSlot(binding.ivSelectedImagePreview3);
            binding.tvSelectedImagePreviewMore.setVisibility(View.GONE);
            binding.tvSelectedImagePreviewMore.setText("");
            binding.tvSelectedImagePreviewLabel.setText(R.string.chat_image_selected);
            return;
        }

        binding.layoutImagePreview.setVisibility(View.VISIBLE);

        int totalCount = pendingImageUris.size();
        binding.tvSelectedImagePreviewLabel.setText(
                getResources().getQuantityString(
                        R.plurals.chat_images_selected_count,
                        totalCount,
                        totalCount
                )
        );

        renderPreviewSlot(binding.slotSelectedImage1, binding.ivSelectedImagePreview1, 0);
        renderPreviewSlot(binding.slotSelectedImage2, binding.ivSelectedImagePreview2, 1);
        renderPreviewSlot(binding.slotSelectedImage3, binding.ivSelectedImagePreview3, 2);

        if (totalCount > 3) {
            binding.tvSelectedImagePreviewMore.setVisibility(View.VISIBLE);
            binding.tvSelectedImagePreviewMore.setText(
                    getString(R.string.chat_images_more_count, totalCount - 3)
            );
        } else {
            binding.tvSelectedImagePreviewMore.setVisibility(View.GONE);
            binding.tvSelectedImagePreviewMore.setText("");
        }
    }

    private void renderPreviewSlot(View slotContainer, android.widget.ImageView previewView, int index) {
        if (index >= pendingImageUris.size()) {
            slotContainer.setVisibility(View.GONE);
            clearPreviewSlot(previewView);
            return;
        }

        slotContainer.setVisibility(View.VISIBLE);
        Uri imageUri = pendingImageUris.get(index);
        if (imageUri == null) {
            clearPreviewSlot(previewView);
            return;
        }
        previewView.setImageURI(imageUri);
    }

    private void clearPreviewSlot(android.widget.ImageView previewView) {
        if (previewView != null) {
            previewView.setImageDrawable(null);
        }
    }

    private void clearPendingImagePreview() {
        pendingImageUris.clear();
        renderImagePreview();
        updateSendButtonState(hasTypedText());
    }

    private void renderPartnerAvatar() {
        AvatarLoader.load(binding.ivPartnerAvatar, normalizeUrl(partnerAvatar));
    }

    private void hydratePartnerProfileIfNeeded() {
        if (apiService == null || partnerId == null || partnerId.trim().isEmpty()) {
            return;
        }
        if (!isBlank(partnerAvatar) && !isBlank(partnerName)) {
            return;
        }

        apiService.getUserProfile(partnerId).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(Call<UserProfileResponse> call, Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                UserProfileResponse profile = response.body();
                if (isBlank(partnerName) && !isBlank(profile.getUsername())) {
                    partnerName = profile.getUsername();
                    // Cache name
                    WebSocketManager.getInstance(ChatActivity.this).cacheUser(partnerId, partnerName);
                }
                if (isBlank(partnerAvatar) && !isBlank(profile.getAvatarUrl())) {
                    partnerAvatar = profile.getAvatarUrl();
                    renderPartnerAvatar();
                }
                renderPartnerHeader(partnerOnline);
            }

            @Override
            public void onFailure(Call<UserProfileResponse> call, Throwable t) {
            }
        });
    }

    private void renderPartnerHeader(boolean isOnline) {
        String safeName = partnerName == null || partnerName.trim().isEmpty() ? "Chat" : partnerName;
        binding.tvPartnerName.setText(safeName);
        binding.tvPartnerStatus.setText(isOnline ? R.string.chat_online_now : R.string.chat_offline_now);
        binding.tvPartnerStatus.setTextColor(getColor(isOnline ? R.color.login_bg_start : R.color.login_text_secondary));
        binding.viewPartnerOnline.setVisibility(isOnline ? View.VISIBLE : View.GONE);
        renderPartnerStreak();
    }

    private void renderPartnerStreak() {
        if (binding.layoutChatStreak == null) {
            return;
        }

        boolean hasActiveStreak = partnerHasFiredStreak && partnerStreakCount > 0;
        binding.layoutChatStreak.setVisibility(View.VISIBLE);
        binding.tvChatStreakCount.setVisibility(hasActiveStreak ? View.VISIBLE : View.GONE);
        binding.tvChatStreakCount.setText(String.valueOf(Math.max(0, partnerStreakCount)));
        binding.ivChatStreakFlame.setColorFilter(ContextCompat.getColor(
                this,
                hasActiveStreak ? R.color.streak_color : R.color.streak_color_inactive
        ));
        binding.ivChatStreakFlame.setAlpha(hasActiveStreak ? 1f : 0.82f);
    }

    private void schedulePartnerConversationMetaRefresh() {
        if (binding == null || isBlank(partnerId)) {
            return;
        }
        binding.topBar.removeCallbacks(refreshPartnerConversationMetaRunnable);
        binding.topBar.postDelayed(refreshPartnerConversationMetaRunnable, 500L);
    }

    private void fetchPartnerConversationMeta() {
        if (apiService == null || isBlank(partnerId)) {
            return;
        }

        apiService.getOrCreateDirectConversation(partnerId).enqueue(new Callback<Conversation>() {
            @Override
            public void onResponse(Call<Conversation> call, Response<Conversation> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getParticipants() == null) {
                    renderPartnerHeader(partnerOnline);
                    return;
                }

                Conversation conversation = response.body();
                for (Conversation.Participant participant : conversation.getParticipants()) {
                    if (participant == null || participant.getId() == null || !participant.getId().equals(partnerId)) {
                        continue;
                    }
                    if (!isBlank(participant.getUsername())) {
                        partnerName = participant.getUsername();
                        WebSocketManager.getInstance(ChatActivity.this).cacheUser(partnerId, partnerName);
                    }
                    if (!isBlank(participant.getAvatarUrl())) {
                        partnerAvatar = participant.getAvatarUrl();
                        renderPartnerAvatar();
                    }
                    if (!isBlank(participant.getEmail())) {
                        partnerEmail = participant.getEmail();
                    }
                    partnerStreakCount = Math.max(0, participant.getStreakCount());
                    partnerHasFiredStreak = participant.isHasFiredStreak();
                    break;
                }
                renderPartnerHeader(partnerOnline);
            }

            @Override
            public void onFailure(Call<Conversation> call, Throwable t) {
                renderPartnerHeader(partnerOnline);
            }
        });
    }

    private void updateSendButtonState(boolean hasText) {
        boolean hasPendingImage = !pendingImageUris.isEmpty();
        boolean enabled = !isImageUploading && (hasText || hasPendingImage);
        binding.btnSend.setEnabled(enabled);
        binding.btnSend.setAlpha(enabled ? 1f : 0.55f);
    }

    private boolean hasTypedText() {
        CharSequence input = binding.etMessage.getText();
        return input != null && !input.toString().trim().isEmpty();
    }

    private void readIntent() {
        conversationId = readStringExtra(EXTRA_CONVERSATION_ID, EXTRA_CONVERSATION_ID_FALLBACK);
        partnerName = readStringExtra(EXTRA_PARTNER_NAME, EXTRA_PARTNER_NAME_FALLBACK);
        partnerId = readStringExtra(EXTRA_PARTNER_ID, EXTRA_PARTNER_ID_FALLBACK);
        partnerEmail = readStringExtra(EXTRA_PARTNER_EMAIL, EXTRA_PARTNER_EMAIL_FALLBACK);
        partnerAvatar = readStringExtra(EXTRA_PARTNER_AVATAR, EXTRA_PARTNER_AVATAR_FALLBACK);
        partnerOnline = readBooleanExtra(EXTRA_PARTNER_ONLINE, EXTRA_PARTNER_ONLINE_FALLBACK, false);

        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(this, R.string.chat_missing_conversation, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private String readStringExtra(String primaryKey, String fallbackKey) {
        String primary = getIntent().getStringExtra(primaryKey);
        if (primary != null && !primary.trim().isEmpty()) {
            return primary;
        }
        String fallback = getIntent().getStringExtra(fallbackKey);
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return primary;
    }

    private boolean readBooleanExtra(String primaryKey, String fallbackKey, boolean defaultValue) {
        if (getIntent().hasExtra(primaryKey)) {
            return getIntent().getBooleanExtra(primaryKey, defaultValue);
        }
        if (getIntent().hasExtra(fallbackKey)) {
            return getIntent().getBooleanExtra(fallbackKey, defaultValue);
        }
        return defaultValue;
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }

        android.net.Uri uri = android.net.Uri.parse(rawUrl);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
