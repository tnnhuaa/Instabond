package com.example.instabond_fe.view.component;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ViewInstaBottomNavBinding;
import com.example.instabond_fe.view.CreatePostActivity;
import com.example.instabond_fe.view.NewsfeedActivity;
import com.example.instabond_fe.view.NotificationsActivity;
import com.example.instabond_fe.view.ProfileActivity;
import com.example.instabond_fe.view.SearchActivity;

public class InstaBottomNavView extends FrameLayout {

    public enum Tab {
        HOME,
        SEARCH,
        NOTIFICATIONS,
        PROFILE
    }

    private final ViewInstaBottomNavBinding binding;

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

    public void bind(Activity activity, Tab activeTab) {
        setActiveTab(activeTab);

        binding.navHome.setOnClickListener(v -> navigateTo(activity, NewsfeedActivity.class));
        binding.navSearch.setOnClickListener(v -> navigateTo(activity, SearchActivity.class));
        binding.navNotifications.setOnClickListener(v -> navigateTo(activity, NotificationsActivity.class));
        binding.navProfile.setOnClickListener(v -> navigateTo(activity, ProfileActivity.class));
        binding.btnCreate.setOnClickListener(v ->
                getContext().startActivity(new Intent(getContext(), CreatePostActivity.class)));
    }

    public void setActiveTab(Tab activeTab) {
        int activeColor = ContextCompat.getColor(getContext(), R.color.text_primary);
        int inactiveColor = ContextCompat.getColor(getContext(), R.color.text_secondary);

        applyState(binding.ivNavHome, binding.tvNavHome, activeTab == Tab.HOME, activeColor, inactiveColor);
        applyState(binding.ivNavSearch, binding.tvNavSearch, activeTab == Tab.SEARCH, activeColor, inactiveColor);
        applyState(binding.ivNavNotifications, binding.tvNavNotifications, activeTab == Tab.NOTIFICATIONS, activeColor, inactiveColor);
        applyState(binding.ivNavProfile, binding.tvNavProfile, activeTab == Tab.PROFILE, activeColor, inactiveColor);
    }

    private void applyState(ImageView icon, TextView label, boolean active, int activeColor, int inactiveColor) {
        int color = active ? activeColor : inactiveColor;
        icon.setImageTintList(ColorStateList.valueOf(color));
        label.setTextColor(color);
    }

    private void navigateTo(Activity activity, Class<?> destination) {
        if (activity.getClass().equals(destination)) {
            return;
        }
        Intent intent = new Intent(activity, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
