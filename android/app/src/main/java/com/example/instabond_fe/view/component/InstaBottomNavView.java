package com.example.instabond_fe.view.component;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ViewInstaBottomNavBinding;
import com.example.instabond_fe.repository.NotificationCountManager;
import com.example.instabond_fe.view.CreatePostActivity;
import com.example.instabond_fe.view.NewsfeedActivity;
import com.example.instabond_fe.view.NotificationsActivity;
import com.example.instabond_fe.view.ProfileActivity;
import com.example.instabond_fe.view.SearchActivity;

public class InstaBottomNavView extends FrameLayout {

    public enum Tab {
        HOME,
        SEARCH,
        CREATE,
        NOTIFICATIONS,
        PROFILE
    }

    public interface OnTabReselectedListener {
        void onTabReselected(Tab tab);
    }

    private final ViewInstaBottomNavBinding binding;
    private NotificationCountManager countManager;
    private OnTabReselectedListener tabReselectedListener;
    private final NotificationCountManager.UnreadCountListener unreadCountListener = count ->
            setNotificationsBadgeVisible(count > 0);

    public InstaBottomNavView(@NonNull Context context) {
        this(context, null);
    }

    public InstaBottomNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InstaBottomNavView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = ViewInstaBottomNavBinding.inflate(LayoutInflater.from(context), this);
    }

    public void setOnTabReselectedListener(OnTabReselectedListener listener) {
        this.tabReselectedListener = listener;
    }

    public void bind(Activity activity, Tab activeTab) {
        setActiveTab(activeTab);
        
        // Initialize notification count manager
        countManager = NotificationCountManager.getInstance(getContext());
        countManager.addListener(unreadCountListener);
        // Set initial badge state
        setNotificationsBadgeVisible(countManager.getUnreadCount() > 0);

        binding.navHome.setOnClickListener(v -> handleTabClick(activity, NewsfeedActivity.class, Tab.HOME));
        binding.navSearch.setOnClickListener(v -> handleTabClick(activity, SearchActivity.class, Tab.SEARCH));
        binding.navNotifications.setOnClickListener(v -> handleTabClick(activity, NotificationsActivity.class, Tab.NOTIFICATIONS));
        binding.navProfile.setOnClickListener(v -> handleTabClick(activity, ProfileActivity.class, Tab.PROFILE));
        binding.btnCreate.setOnClickListener(v ->
                getContext().startActivity(new Intent(getContext(), CreatePostActivity.class)));
    }

    public void setActiveTab(Tab activeTab) {
        int activeColor = ContextCompat.getColor(getContext(), R.color.bottom_nav_active);
        int inactiveColor = ContextCompat.getColor(getContext(), R.color.bottom_nav_inactive);

        applyState(binding.navHome, binding.ivNavHome, activeTab == Tab.HOME, activeColor, inactiveColor);
        applyState(binding.navSearch, binding.ivNavSearch, activeTab == Tab.SEARCH, activeColor, inactiveColor);
        applyState(binding.btnCreate, binding.ivNavCreate, activeTab == Tab.CREATE, activeColor, inactiveColor);
        applyState(binding.navNotifications, binding.ivNavNotifications, activeTab == Tab.NOTIFICATIONS, activeColor, inactiveColor);
        applyState(binding.navProfile, binding.ivNavProfile, activeTab == Tab.PROFILE, activeColor, inactiveColor);
    }

    public void setNotificationsBadgeVisible(boolean visible) {
        binding.ivNavNotificationsBadge.setVisibility(visible ? VISIBLE : GONE);
    }

    private void handleTabClick(Activity activity, Class<?> destination, Tab clickedTab) {
        if (activity.getClass().equals(destination)) {
            if (tabReselectedListener != null) {
                tabReselectedListener.onTabReselected(clickedTab);
            }
            return;
        }

        Intent intent = new Intent(activity, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    private void applyState(FrameLayout container, ImageView icon, boolean active, int activeColor, int inactiveColor) {
        int color = active ? activeColor : inactiveColor;
        icon.setImageTintList(ColorStateList.valueOf(color));
        container.setBackgroundResource(active
                ? R.drawable.feed_bottom_nav_active_bg
                : android.R.color.transparent);
    }
}
