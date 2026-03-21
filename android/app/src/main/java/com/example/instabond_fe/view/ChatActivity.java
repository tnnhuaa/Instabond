package com.example.instabond_fe.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityChatBinding;
import com.example.instabond_fe.viewmodel.ChatViewModel;

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

        readIntent();

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        messageAdapter = new ChatMessageAdapter(viewModel.getCurrentUserId());

        binding.rvMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMessages.setAdapter(messageAdapter);

        if (partnerAvatar != null && !partnerAvatar.trim().isEmpty()) {
            Glide.with(this)
                    .load(partnerAvatar)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivPartnerAvatar);
        } else {
            binding.ivPartnerAvatar.setImageResource(R.drawable.ic_person);
        }

        bindObservers();
        bindActions();

        viewModel.startChat(conversationId, partnerId, partnerEmail, partnerOnline);
        renderPartnerHeader(partnerOnline);
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.ensureRealtimeConnected();
    }

    @Override
    protected void onDestroy() {
        viewModel.stopChat();
        super.onDestroy();
    }

    private void bindObservers() {
        viewModel.getMessagesLiveData().observe(this, messages -> {
            runOnUiThread(() -> {
                // ChatViewModel routes only messages that match current conversationId.
                messageAdapter.submitList(messages);
                if (messages != null && !messages.isEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size() - 1);
                }
            });
        });

        viewModel.getConnectionLiveData().observe(this, connected -> {
            runOnUiThread(() -> {
                if (Boolean.TRUE.equals(connected)) {
                    binding.etMessage.setHint("Type a message...");
                } else {
                    binding.etMessage.setHint("Reconnecting...");
                }
            });
        });

        viewModel.getPartnerOnlineLiveData().observe(this, isOnline ->
                runOnUiThread(() -> renderPartnerHeader(Boolean.TRUE.equals(isOnline))));

        viewModel.getErrorLiveData().observe(this, error -> {
            runOnUiThread(() -> {
                if (error != null && !error.trim().isEmpty()) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                }
            });
        });
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
                binding.btnSend.setVisibility(hasText ? android.view.View.VISIBLE : android.view.View.GONE);
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

    private void renderPartnerHeader(boolean isOnline) {
        String safeName = partnerName == null || partnerName.trim().isEmpty() ? "Chat" : partnerName;
        // @TODO - Process status of partner: String status = isOnline ? "Online" : "Offline";
        binding.tvPartnerName.setText(safeName);
    }

    private void readIntent() {
        conversationId = readStringExtra(EXTRA_CONVERSATION_ID, EXTRA_CONVERSATION_ID_FALLBACK);
        partnerName = readStringExtra(EXTRA_PARTNER_NAME, EXTRA_PARTNER_NAME_FALLBACK);
        partnerId = readStringExtra(EXTRA_PARTNER_ID, EXTRA_PARTNER_ID_FALLBACK);
        partnerEmail = readStringExtra(EXTRA_PARTNER_EMAIL, EXTRA_PARTNER_EMAIL_FALLBACK);
        partnerAvatar = readStringExtra(EXTRA_PARTNER_AVATAR, EXTRA_PARTNER_AVATAR_FALLBACK);
        partnerOnline = readBooleanExtra(EXTRA_PARTNER_ONLINE, EXTRA_PARTNER_ONLINE_FALLBACK, false);

        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(this, "Missing conversation", Toast.LENGTH_SHORT).show();
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
}
