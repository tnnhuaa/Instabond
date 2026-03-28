package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.StoryItem;

import java.util.ArrayList;
import java.util.List;

public class StoryFeedAdapter extends RecyclerView.Adapter<StoryFeedAdapter.StoryViewHolder> {
    private static final int ACTIVE_STORY_INNER_PADDING_DP = 2;

    public interface Listener {
        void onCreateStoryClicked();
        void onStoryClicked(StoryItem item);
    }

    private final List<StoryItem> items = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submitItems(List<StoryItem> storyItems) {
        items.clear();
        if (storyItems != null) {
            items.addAll(storyItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_story_feed, parent, false);
        return new StoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        StoryItem item = items.get(position);
        boolean hasActiveStory = item.hasMedia();
        holder.tvName.setText(item.isCreateCard()
                ? holder.itemView.getContext().getString(R.string.feed_story_your_story)
                : item.getUsername());

        holder.plusBadge.setVisibility(item.isCreateCard() ? View.VISIBLE : View.GONE);
        holder.ringFrame.setBackgroundResource(item.isCreateCard() && !hasActiveStory
                ? R.drawable.feed_story_add_bg
                : R.drawable.feed_story_active_ring);
        holder.innerFrame.setBackgroundResource(item.isCreateCard() && !hasActiveStory
                ? android.R.color.transparent
                : R.drawable.feed_story_inner_frame_bg);
        holder.innerFrame.setPadding(0, 0, 0, 0);
        if (hasActiveStory || !item.isCreateCard()) {
            int padding = Math.round(holder.itemView.getResources().getDisplayMetrics().density
                    * ACTIVE_STORY_INNER_PADDING_DP);
            holder.innerFrame.setPadding(padding, padding, padding, padding);
        }

        Glide.with(holder.itemView)
                .load(item.getAvatarUrl())
                .transform(new CircleCrop())
                .placeholder(R.drawable.profile_placeholder_bg)
                .error(R.drawable.profile_placeholder_bg)
                .into(holder.ivAvatar);

        holder.itemView.setOnClickListener(v -> {
            if (listener == null) {
                return;
            }
            if (item.isCreateCard() && !hasActiveStory) {
                listener.onCreateStoryClicked();
            } else {
                listener.onStoryClicked(item);
            }
        });

        holder.plusBadge.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCreateStoryClicked();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StoryViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout ringFrame;
        private final FrameLayout innerFrame;
        private final ImageView ivAvatar;
        private final View plusBadge;
        private final TextView tvName;

        StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ringFrame = itemView.findViewById(R.id.story_ring_frame);
            innerFrame = itemView.findViewById(R.id.story_inner_frame);
            ivAvatar = itemView.findViewById(R.id.iv_story_avatar);
            plusBadge = itemView.findViewById(R.id.story_plus_badge);
            tvName = itemView.findViewById(R.id.tv_story_name);
        }
    }
}
