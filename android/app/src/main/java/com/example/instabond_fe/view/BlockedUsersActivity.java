package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
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
import com.example.instabond_fe.utils.LocaleManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BlockedUsersActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private ApiService apiService;
    private UserAdapter adapter;
    private ProgressBar progressBar;
    private SessionManager sessionManager;
    private FloatingActionButton fabAddBlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_list);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getApiService(this);

        Toolbar toolbar = findViewById(R.id.toolbar_list);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.settings_blocked_users));
        }
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(getResources().getColor(R.color.login_bg_start, null));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        RecyclerView rvUsers = findViewById(R.id.rv_users);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));

        fabAddBlock = findViewById(R.id.fab_add_block);
        if (fabAddBlock != null) {
            fabAddBlock.setVisibility(View.VISIBLE);
            fabAddBlock.setOnClickListener(v -> showAddBlockDialog());
        }

        setupUserAdapter();
        loadBlockedUsers();
    }

    private void setupUserAdapter() {
        adapter = new UserAdapter();
        adapter.setProfileOwnerId(sessionManager.getUserId());
        adapter.setCurrentUserId(sessionManager.getUserId());
        adapter.setMode("blocked");
        adapter.setListener(new UserAdapter.OnUserInteractionListener() {
            @Override
            public void onUserClicked(FollowUserResponse user) {
                Intent intent = new Intent(BlockedUsersActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", user.getId());
                startActivity(intent);
            }

            @Override
            public void onUserLongClicked(FollowUserResponse user, int position) {
                Toast.makeText(BlockedUsersActivity.this, "Nhấn nút để bỏ chặn", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onActionClicked(FollowUserResponse user, int position) {
                apiService.unblockUser(user.getId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(BlockedUsersActivity.this, "Đã bỏ chặn", Toast.LENGTH_SHORT).show();
                            loadBlockedUsers();
                        } else {
                            Toast.makeText(BlockedUsersActivity.this, "Bỏ chặn thất bại", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(BlockedUsersActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
                    }
                });
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
                    Toast.makeText(BlockedUsersActivity.this, getString(R.string.blocked_users_empty), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(BlockedUsersActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddBlockDialog() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getFollowers(sessionManager.getUserId()).enqueue(new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    showFollowersSelectionDialog(response.body());
                } else {
                    Toast.makeText(BlockedUsersActivity.this, "Không thể tải danh sách followers", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(BlockedUsersActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFollowersSelectionDialog(List<FollowUserResponse> followers) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_friend_suggestions, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_suggestions);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        BlockUserSelectionAdapter adapter = new BlockUserSelectionAdapter(followers, user -> {
            blockUserAndRefresh(user.getId());
        });
        recyclerView.setAdapter(adapter);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Chọn người để chặn")
                .setView(dialogView)
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(d -> {
            int textColor = ContextCompat.getColor(this, R.color.settings_text_primary);
            android.widget.Button negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(textColor);
            }
        });

        dialog.show();
    }

    private void blockUserAndRefresh(String userId) {
        apiService.blockUser(userId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(BlockedUsersActivity.this, "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                    loadBlockedUsers();
                } else {
                    Toast.makeText(BlockedUsersActivity.this, "Chặn thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(BlockedUsersActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

