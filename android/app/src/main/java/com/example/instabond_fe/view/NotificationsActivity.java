package com.example.instabond_fe.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityNotificationsBinding;
import com.example.instabond_fe.model.NotificationItem;

import java.util.Arrays;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private static final String PREVIEW_LIKE = "https://www.figma.com/api/mcp/asset/c7cd29ba-f130-4fc7-b2b5-8e4063bf94d2";
    private static final String PREVIEW_TAG = "https://www.figma.com/api/mcp/asset/6357ce88-3cfb-4122-8a92-3c652aa8222a";
    private static final String PREVIEW_COMMENT = "https://www.figma.com/api/mcp/asset/cccb5be8-c5f6-49b0-9ca2-81c783d3e44d";

    private ActivityNotificationsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(new NotificationAdapter(buildItems()));

        binding.bottomNav.setSelectedItemId(R.id.nav_notifications);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_notifications) {
                return true;
            }
            if (itemId == R.id.nav_home) {
                openScreen(NewsfeedActivity.class);
                return true;
            }
            if (itemId == R.id.nav_profile) {
                openScreen(ProfileActivity.class);
                return true;
            }
            if (itemId == R.id.nav_create) {
                startActivity(new Intent(this, CreatePostActivity.class));
                return true;
            }
            if (itemId == R.id.nav_search) {
                Toast.makeText(this, R.string.search_coming_soon, Toast.LENGTH_SHORT).show();
                return false;
            }
            return false;
        });
    }

    private void openScreen(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private List<NotificationItem> buildItems() {
        return Arrays.asList(
                new NotificationItem(
                        "Tuấn",
                        "đã thích bài viết của bạn",
                        "5 phút trước",
                        true,
                        PREVIEW_LIKE,
                        R.drawable.ic_heart,
                        R.color.notification_red,
                        false
                ),
                new NotificationItem(
                        "",
                        "vừa phát hiện bạn trong ảnh của Tuấn",
                        "15 phút trước",
                        false,
                        PREVIEW_TAG,
                        R.drawable.ic_tag,
                        R.color.text_primary,
                        true
                ),
                new NotificationItem(
                        "Linh",
                        "Bạn và Linh vừa trở thành Tri kỷ! 🎉",
                        "1 giờ trước",
                        true,
                        null,
                        R.drawable.ic_trophy,
                        R.color.notification_yellow,
                        false
                ),
                new NotificationItem(
                        "",
                        "Streak 10 tuần với Hương! Tiếp tục nhé 🔥",
                        "2 giờ trước",
                        false,
                        null,
                        R.drawable.ic_flame,
                        R.color.notification_orange,
                        false
                ),
                new NotificationItem(
                        "Nam",
                        "đã bình luận: \"Ảnh đẹp quá!\"",
                        "3 giờ trước",
                        true,
                        PREVIEW_COMMENT,
                        R.drawable.ic_heart,
                        R.color.notification_muted_icon,
                        false
                ),
                new NotificationItem(
                        "An",
                        "đã chấp nhận lời mời kết bạn",
                        "5 giờ trước",
                        true,
                        null,
                        R.drawable.ic_user_plus,
                        R.color.notification_green,
                        false
                )
        );
    }
}
