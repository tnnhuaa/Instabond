package com.example.instabond_fe.view;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.model.CreateCommentRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentActivity extends AppCompatActivity {

    private String postId;
    private String postUsername;
    private String postCaption;
    private String postAvatarUrl;
    private String postCreatedAt;

    private ApiService apiService;
    private CommentAdapter adapter;

    private RecyclerView rvComments;
    private EditText etComment;
    private TextView btnPostComment;
    private ProgressBar progressBar;
    private ImageView ivPostAuthorAvatar;
    private TextView tvPostAuthorUsername;
    private TextView tvPostTime;
    private TextView tvPostCaption;
    private ImageView ivCurrentUserAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        postId = getIntent().getStringExtra("postId");
        postUsername = valueOrEmpty(getIntent().getStringExtra("postUsername"));
        postCaption = valueOrEmpty(getIntent().getStringExtra("postCaption"));
        postAvatarUrl = valueOrEmpty(getIntent().getStringExtra("postAvatarUrl"));
        postCreatedAt = valueOrEmpty(getIntent().getStringExtra("postCreatedAt"));

        if (postId == null || postId.trim().isEmpty()) {
            Toast.makeText(this, R.string.comment_missing_post, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);
        bindViews();
        bindPostHeader();
        setupCommentList();
        setupActions();
        loadCurrentUser();
        loadComments();
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        rvComments = findViewById(R.id.rv_comments);
        etComment = findViewById(R.id.et_comment);
        btnPostComment = findViewById(R.id.btn_post_comment);
        progressBar = findViewById(R.id.progress_bar);
        ivPostAuthorAvatar = findViewById(R.id.iv_post_author_avatar);
        tvPostAuthorUsername = findViewById(R.id.tv_post_author_username);
        tvPostTime = findViewById(R.id.tv_post_time);
        tvPostCaption = findViewById(R.id.tv_post_caption);
        ivCurrentUserAvatar = findViewById(R.id.iv_current_user_avatar);

        TextView[] emojiButtons = new TextView[]{
                findViewById(R.id.emoji_heart),
                findViewById(R.id.emoji_raise_hands),
                findViewById(R.id.emoji_sparkles),
                findViewById(R.id.emoji_fire),
                findViewById(R.id.emoji_heart_eyes),
                findViewById(R.id.emoji_clap)
        };
        String[] emojis = new String[]{"❤️", "🙌", "✨", "🔥", "😍", "👏"};
        for (int i = 0; i < emojiButtons.length; i++) {
            final String emoji = emojis[i];
            emojiButtons[i].setOnClickListener(v -> appendEmoji(emoji));
        }
    }

    private void bindPostHeader() {
        tvPostAuthorUsername.setText(postUsername.isEmpty() ? "unknown" : postUsername);
        tvPostCaption.setText(postCaption);
        tvPostCaption.setVisibility(postCaption.isEmpty() ? View.GONE : View.VISIBLE);

        String compactTime = TimeUtils.getCompactRelativeTime(postCreatedAt);
        boolean hasTime = !postCreatedAt.isEmpty() && !"JUST NOW".equals(compactTime);
        tvPostTime.setText(compactTime);
        tvPostTime.setVisibility(hasTime ? View.VISIBLE : View.GONE);

        Glide.with(this)
                .load(normalizeUrl(postAvatarUrl))
                .circleCrop()
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(ivPostAuthorAvatar);
    }

    private void setupCommentList() {
        adapter = new CommentAdapter(postUsername);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(adapter);
        rvComments.setClipToPadding(false);
    }

    private void setupActions() {
        btnPostComment.setOnClickListener(v -> postComment());
    }

    private void loadCurrentUser() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call,
                                   @NonNull Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    ivCurrentUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
                    return;
                }

                String avatarUrl = response.body().getAvatarUrl();
                Glide.with(CommentActivity.this)
                        .load(normalizeUrl(avatarUrl))
                        .circleCrop()
                        .placeholder(R.drawable.profile_placeholder_bg)
                        .error(R.drawable.profile_placeholder_bg)
                        .into(ivCurrentUserAvatar);
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                ivCurrentUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
            }
        });
    }

    private void loadComments() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getComments(postId).enqueue(new Callback<List<CommentResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<CommentResponse>> call,
                                   @NonNull Response<List<CommentResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setComments(response.body());
                } else {
                    Toast.makeText(CommentActivity.this, R.string.comment_load_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CommentResponse>> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(
                        CommentActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void postComment() {
        String content = etComment.getText().toString().trim();
        if (content.isEmpty()) {
            return;
        }

        btnPostComment.setEnabled(false);
        CreateCommentRequest req = new CreateCommentRequest(content);

        apiService.addComment(postId, req).enqueue(new Callback<CommentResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentResponse> call,
                                   @NonNull Response<CommentResponse> response) {
                btnPostComment.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    etComment.setText("");
                    adapter.addComment(response.body());
                    rvComments.scrollToPosition(0);
                } else {
                    Toast.makeText(CommentActivity.this, R.string.comment_post_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CommentResponse> call, @NonNull Throwable t) {
                btnPostComment.setEnabled(true);
                Toast.makeText(
                        CommentActivity.this,
                        getString(R.string.msg_connection_error, valueOrEmpty(t.getMessage())),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void appendEmoji(String emoji) {
        String current = etComment.getText() == null ? "" : etComment.getText().toString();
        String spacer = current.isEmpty() || current.endsWith(" ") ? "" : " ";
        etComment.append(spacer + emoji);
        etComment.requestFocus();
        etComment.setSelection(etComment.getText().length());
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
