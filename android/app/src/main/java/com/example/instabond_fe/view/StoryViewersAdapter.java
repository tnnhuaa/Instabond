package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.StoryViewerResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class StoryViewersAdapter extends RecyclerView.Adapter<StoryViewersAdapter.ViewerViewHolder> {
    private final List<StoryViewerResponse> items = new ArrayList<>();

    public void submitList(List<StoryViewerResponse> viewers) {
        items.clear();
        if (viewers != null) {
            items.addAll(viewers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View root = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_story_viewer_user, parent, false);
        return new ViewerViewHolder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewerViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewerViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivAvatar;
        private final ImageView ivHeartBadge;
        private final TextView tvUsername;
        private final TextView tvMeta;

        ViewerViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_viewer_avatar);
            ivHeartBadge = itemView.findViewById(R.id.iv_viewer_heart_badge);
            tvUsername = itemView.findViewById(R.id.tv_viewer_username);
            tvMeta = itemView.findViewById(R.id.tv_viewer_meta);
        }

        void bind(StoryViewerResponse viewer) {
            if (viewer == null) {
                return;
            }

            AvatarLoader.load(ivAvatar, normalizeUrl(viewer.getAvatarUrl()));
            String primaryName = viewer.getUsername() == null || viewer.getUsername().trim().isEmpty()
                    ? itemView.getContext().getString(R.string.story_view_unknown_viewer)
                    : viewer.getUsername();
            tvUsername.setText(primaryName);

            String secondary = TimeUtils.getConversationTimeLabel(viewer.getViewedAt());
            if (viewer.getFullName() != null && !viewer.getFullName().trim().isEmpty()) {
                secondary = viewer.getFullName() + " • " + secondary;
            }
            tvMeta.setText(secondary);
            ivHeartBadge.setVisibility(viewer.isLiked() ? View.VISIBLE : View.GONE);
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
    }
}
