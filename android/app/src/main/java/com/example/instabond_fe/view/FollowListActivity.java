package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

    public static final String EXTRA_MODE = "mode"; // "followers" or "following"
    public static final String EXTRA_USER_ID = "userId";
    
    private String mode;
    private String userId;
    
    private ApiService apiService;
    private UserAdapter adapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        userId = getIntent().getStringExtra(EXTRA_USER_ID);

        if (mode == null || userId == null) {
            Toast.makeText(this, "Lỗi hiển thị danh sách", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiService = ApiClient.getApiService(this);

        Toolbar toolbar = findViewById(R.id.toolbar_list);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("followers".equals(mode) ? "Người theo dõi" : "Đang theo dõi");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        RecyclerView rvUsers = findViewById(R.id.rv_users);

        adapter = new UserAdapter();
        adapter.setProfileOwnerId(userId); // Set the ID of the profile owner we are viewing
        SessionManager sessionManager = new SessionManager(this);
        adapter.setCurrentUserId(sessionManager.getUserId());
        adapter.setListener(new UserAdapter.OnUserInteractionListener() {
            @Override
            public void onUserClicked(FollowUserResponse user) {
                Intent intent = new Intent(FollowListActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", user.getId());
                startActivity(intent);
            }
            @Override
            public void onUserLongClicked(FollowUserResponse user, int position) {
                // Chỉ cho phép đặt bạn thân nếu đã là bạn bè/đang theo dõi
                if (!"accepted".equals(user.getRelationshipStatus())) {
                    Toast.makeText(FollowListActivity.this, "Hãy theo dõi người này trước khi đặt làm bạn thân", Toast.LENGTH_SHORT).show();
                    return;
                }

                String title = user.isCloseFriend() ? "Xóa khỏi Bạn thân?" : "Thêm vào Bạn thân?";
                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(FollowListActivity.this)
                        .setTitle(title)
                        .setMessage("Bạn có muốn thay đổi trạng thái bạn thân cho @" + user.getUsername() + " không?")
                        .setPositiveButton("Xác nhận", (d, w) -> toggleCloseFriend(user, position))
                        .setNegativeButton("Hủy", null)
                        .show();

                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.GRAY);
            }
            @Override
            public void onActionClicked(FollowUserResponse user, int position) {
                String currentStatus = user.getRelationshipStatus();
                if ("accepted".equals(currentStatus) || "pending".equals(currentStatus) || user.isMutualFollow()) {
                    // Unfollow
                    apiService.unfollowUser(user.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                user.setRelationshipStatus("none");
                                user.setMutualFollow(false);
                                adapter.notifyItemChanged(position);
                            } else {
                                Toast.makeText(FollowListActivity.this, "Lỗi bỏ theo dõi", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(FollowListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // Follow
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
                            } else {
                                Toast.makeText(FollowListActivity.this, "Lỗi theo dõi", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                            Toast.makeText(FollowListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        loadList();
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
}
