package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FollowListActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_USER_ID = "userId";

    private String mode;
    private String userId;
    private SessionManager sessionManager;

    private ApiService apiService;
    private UserAdapter adapter;
    private ProgressBar progressBar;

    private FollowRequestAdapter requestAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        userId = getIntent().getStringExtra(EXTRA_USER_ID);

        if (mode == null) {
            Toast.makeText(this, "Lỗi hiển thị danh sách", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sessionManager = new SessionManager(this);
        if (userId == null || userId.trim().isEmpty()) {
            userId = sessionManager.getUserId();
        }

        apiService = ApiClient.getApiService(this);

        Toolbar toolbar = findViewById(R.id.toolbar_list);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if ("requests".equals(mode)) {
                getSupportActionBar().setTitle("Follow requests");
            } else if ("blocked".equals(mode)) {
                getSupportActionBar().setTitle(getString(R.string.blocked_users_title));
            } else {
                getSupportActionBar().setTitle("followers".equals(mode) ? "Người theo dõi" : "Đang theo dõi");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(getResources().getColor(R.color.login_bg_start, null));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        RecyclerView rvUsers = findViewById(R.id.rv_users);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));

        if ("requests".equals(mode)) {
            setupRequestAdapter();
            loadRequests();
        } else if ("blocked".equals(mode)) {
            setupUserAdapter();
            loadBlockedUsers();
        } else {
            setupUserAdapter();
            loadList();
        }
    }

    private void loadList() {
        progressBar.setVisibility(View.VISIBLE);
        Callback<List<FollowUserResponse>> callback = new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setUsers(response.body());
                } else {
                    Toast.makeText(FollowListActivity.this, "Không thể tải danh sách", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(FollowListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        };

        if ("followers".equals(mode)) {
            apiService.getFollowers(userId).enqueue(callback);
        } else {
            apiService.getFollowing(userId).enqueue(callback);
        }
    }

    private void toggleCloseFriend(FollowUserResponse user, int position) {
        boolean nextStatus = !user.isCloseFriend();
        apiService.setCloseFriend(user.getId(), nextStatus).enqueue(new Callback<FollowUserResponse>() {
            @Override
            public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                if (response.isSuccessful()) {
                    user.setCloseFriend(nextStatus);
                    adapter.notifyItemChanged(position);
                    Toast.makeText(FollowListActivity.this, "Đã cập nhật danh sách Bạn thân!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FollowListActivity.this, "Lỗi từ Server", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                Toast.makeText(FollowListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRequestAdapter() {
        requestAdapter = new FollowRequestAdapter(new FollowRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(FollowUserResponse user, int position) {
                apiService.acceptFollowRequest(user.getId()).enqueue(new Callback<FollowUserResponse>() {
                    @Override
                    public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                        if (response.isSuccessful()) {
                            requestAdapter.removeRequest(position);
                            Toast.makeText(FollowListActivity.this, "Đã xác nhận", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<FollowUserResponse> call, Throwable t) {}
                });
            }

            @Override
            public void onReject(FollowUserResponse user, int position) {
                apiService.rejectFollowRequest(user.getId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            requestAdapter.removeRequest(position);
                            Toast.makeText(FollowListActivity.this, "Đã xóa yêu cầu", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {}
                });
            }
        });
        RecyclerView rvUsers = findViewById(R.id.rv_users);
        rvUsers.setAdapter(requestAdapter);
    }

    private void setupUserAdapter() {
        adapter = new UserAdapter();
        adapter.setProfileOwnerId(userId);
        adapter.setCurrentUserId(sessionManager.getUserId());
        adapter.setMode(mode);
        adapter.setListener(new UserAdapter.OnUserInteractionListener() {
            @Override
            public void onUserClicked(FollowUserResponse user) {
                Intent intent = new Intent(FollowListActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", user.getId());
                startActivity(intent);
            }
            @Override
            public void onUserLongClicked(FollowUserResponse user, int position) {
                if ("blocked".equals(mode)) {
                    Toast.makeText(FollowListActivity.this, "Nhấn nút để bỏ chặn", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!"accepted".equals(user.getRelationshipStatus())) {
                    Toast.makeText(FollowListActivity.this, "Cần theo dõi trước", Toast.LENGTH_SHORT).show();
                    return;
                }
                String title = user.isCloseFriend() ? "Xóa" : "Thêm";
                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(FollowListActivity.this)
                        .setTitle(title)
                        .setMessage("Xác nhận thao tác đối với người dùng này?")
                        .setPositiveButton("Xác nhận", (d, w) -> toggleCloseFriend(user, position))
                        .setNegativeButton("Hủy", null)
                        .create();

                dialog.setOnShowListener(d -> {
                    int textColor = ContextCompat.getColor(FollowListActivity.this, R.color.settings_text_primary);
                    android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                    android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                    if (negativeButton != null) {
                        negativeButton.setTextColor(textColor);
                    }
                    if (positiveButton != null) {
                        positiveButton.setTextColor(textColor);
                    }
                });

                dialog.show();
            }
            @Override
            public void onActionClicked(FollowUserResponse user, int position) {
                if ("blocked".equals(mode)) {
                    apiService.unblockUser(user.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(FollowListActivity.this, "Đã bỏ chặn", Toast.LENGTH_SHORT).show();
                                loadBlockedUsers();
                            } else {
                                Toast.makeText(FollowListActivity.this, "Bỏ chặn thất bại", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(FollowListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                String currentStatus = user.getRelationshipStatus();
                if ("accepted".equals(currentStatus) || "pending".equals(currentStatus) || user.isMutualFollow()) {
                    apiService.unfollowUser(user.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                user.setRelationshipStatus("none");
                                user.setMutualFollow(false);
                                adapter.notifyItemChanged(position);
                            }
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                } else {
                    apiService.followUser(user.getId()).enqueue(new Callback<FollowUserResponse>() {
                        @Override
                        public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                String newStatus = response.body().getRelationshipStatus();
                                user.setRelationshipStatus(newStatus);
                                if ("followers".equals(mode) && "accepted".equals(newStatus)) {
                                    user.setMutualFollow(true);
                                } else {
                                    user.setMutualFollow(false);
                                }
                                adapter.notifyItemChanged(position);
                            }
                        }
                        @Override
                        public void onFailure(Call<FollowUserResponse> call, Throwable t) {}
                    });
                }
            }
        });
        RecyclerView rvUsers = findViewById(R.id.rv_users);
        rvUsers.setAdapter(adapter);
    }

    private void loadBlockedUsers() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getBlockedUsers().enqueue(new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setUsers(response.body());
                } else {
                    Toast.makeText(FollowListActivity.this, getString(R.string.blocked_users_empty), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(FollowListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRequests() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getIncomingFollowRequests().enqueue(new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    requestAdapter.setRequests(response.body());
                } else {
                    Toast.makeText(FollowListActivity.this, "Không thể tải yêu cầu: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(FollowListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
            }
        });
    }
}