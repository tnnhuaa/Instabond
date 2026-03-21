package com.example.instabond_fe.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.model.CreateCommentRequest;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentActivity extends AppCompatActivity {

    private String postId;
    private ApiService apiService;
    private CommentAdapter adapter;

    private RecyclerView rvComments;
    private EditText etComment;
    private Button btnPostComment;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        postId = getIntent().getStringExtra("postId");
        if (postId == null || postId.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);

        Toolbar toolbar = findViewById(R.id.toolbar_comments);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bình luận");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvComments = findViewById(R.id.rv_comments);
        etComment = findViewById(R.id.et_comment);
        btnPostComment = findViewById(R.id.btn_post_comment);
        progressBar = findViewById(R.id.progress_bar);

        adapter = new CommentAdapter();
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(adapter);

        btnPostComment.setOnClickListener(v -> postComment());

        loadComments();
    }

    private void loadComments() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getComments(postId).enqueue(new Callback<List<CommentResponse>>() {
            @Override
            public void onResponse(Call<List<CommentResponse>> call, Response<List<CommentResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setComments(response.body());
                } else {
                    Toast.makeText(CommentActivity.this, "Không tải được bình luận", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<CommentResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CommentActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
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
            public void onResponse(Call<CommentResponse> call, Response<CommentResponse> response) {
                btnPostComment.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    etComment.setText("");
                    adapter.addComment(response.body());
                    rvComments.scrollToPosition(0);
                } else {
                    Toast.makeText(CommentActivity.this, "Gửi bình luận thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CommentResponse> call, Throwable t) {
                btnPostComment.setEnabled(true);
                Toast.makeText(CommentActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
