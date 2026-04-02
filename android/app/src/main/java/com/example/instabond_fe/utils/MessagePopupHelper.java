package com.example.instabond_fe.utils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.utils.RichMessageUtils;
import com.example.instabond_fe.view.ChatActivity;
import com.google.android.material.snackbar.Snackbar;

public class MessagePopupHelper {

    public static void showInAppNotification(Activity activity, ChatMessageResponse message, String cachedName) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        // Load Layout custom
        View customView = LayoutInflater.from(activity).inflate(R.layout.custom_message_alerter, null);

        TextView customTitle = customView.findViewById(R.id.customTitle);
        TextView customText = customView.findViewById(R.id.customText);

        customTitle.setText(cachedName != null ? cachedName : "New message");
        String preview = RichMessageUtils.getConversationPreview(message);
        customText.setText(preview.isEmpty() ? "You have a new message" : preview);

        // Create Snackbar
        View rootView = activity.findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG);

        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.TRANSPARENT);

        // Adjust layout
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
        params.gravity = Gravity.TOP;
        params.setMargins(24, 100, 24, 0);
        snackbarView.setLayoutParams(params);

        // Cast to ViewGroup to add custom layout
        ViewGroup snackbarViewGroup = (ViewGroup) snackbarView;
        snackbarViewGroup.removeAllViews();
        snackbarViewGroup.setPadding(0, 0, 0, 0);
        snackbarViewGroup.addView(customView);

        // Handle listener
        customView.setOnClickListener(v -> {
            Intent intent = new Intent(activity, ChatActivity.class);
            intent.putExtra("CONVERSATION_ID", message.getConversationId());
            intent.putExtra("PARTNER_ID", message.getSenderId());
            activity.startActivity(intent);
            snackbar.dismiss();
        });

        // Show the Snackbar
        snackbar.show();
    }
}
