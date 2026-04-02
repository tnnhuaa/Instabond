package com.example.instabond_fe.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityChatBinding;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.repository.WebSocketManager;
import com.example.instabond_fe.utils.AvatarFrameResolver;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.viewmodel.ChatViewModel;
import com.example.instabond_fe.model.UserProfileResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {
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

    private String conversationId;
    private String partnerName;
    private String partnerId;
    private String partnerEmail;
    private String partnerAvatar;
    private boolean partnerOnline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        apiService = ApiClient.getApiService(this);

        applyPartnerAvatarFrame();

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
        updateSendButtonState(false);
        hydratePartnerProfileIfNeeded();

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
        }));

        viewModel.getConnectionLiveData().observe(this, connected -> runOnUiThread(() ->
                binding.etMessage.setHint(Boolean.TRUE.equals(connected)
                        ? R.string.chat_type_message
                        : R.string.chat_reconnecting)));

        viewModel.getPartnerOnlineLiveData().observe(this, isOnline ->
                runOnUiThread(() -> renderPartnerHeader(Boolean.TRUE.equals(isOnline))));

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
            viewModel.sendTextMessage(text);
            binding.etMessage.setText("");
        });
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
    }

    private void updateSendButtonState(boolean hasText) {
        binding.btnSend.setEnabled(hasText);
        binding.btnSend.setAlpha(hasText ? 1f : 0.55f);
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

    private void applyPartnerAvatarFrame() {
        AvatarFrameResolver.AvatarFrame frame = AvatarFrameResolver.resolveChatHeaderAvatarFrame();
        binding.partnerAvatarRing.setBackgroundResource(frame.ringBackgroundRes);
        binding.ivPartnerAvatar.setBackgroundResource(frame.avatarBackgroundRes);
        int paddingPx = Math.round(getResources().getDisplayMetrics().density * frame.ringPaddingDp);
        binding.partnerAvatarRing.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    }
}
