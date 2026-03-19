package com.example.instabond_fe.view;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ItemNotificationBinding;
import com.example.instabond_fe.model.NotificationItem;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final List<NotificationItem> items;

    public NotificationAdapter(List<NotificationItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(inflater, parent, false);
        return new NotificationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificationBinding binding;

        NotificationViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotificationItem item) {
            int tint = ContextCompat.getColor(binding.getRoot().getContext(), item.getIconTintRes());
            binding.ivSmallIcon.setImageResource(item.getIconRes());
            binding.ivSmallIcon.setImageTintList(ColorStateList.valueOf(tint));
            binding.ivLargeIcon.setImageResource(item.getIconRes());
            binding.ivLargeIcon.setImageTintList(ColorStateList.valueOf(tint));

            binding.smallChip.setVisibility(item.isLargeChip() ? View.GONE : View.VISIBLE);
            binding.largeChip.setVisibility(item.isLargeChip() ? View.VISIBLE : View.GONE);

            binding.tvMessage.setText(buildMessage(item));
            binding.tvTime.setText(item.getTime());

            if (item.getPreviewUrl() == null || item.getPreviewUrl().trim().isEmpty()) {
                binding.ivPreview.setVisibility(View.GONE);
                Glide.with(binding.ivPreview).clear(binding.ivPreview);
            } else {
                binding.ivPreview.setVisibility(View.VISIBLE);
                Glide.with(binding.ivPreview)
                        .load(item.getPreviewUrl())
                        .placeholder(R.drawable.notification_preview_placeholder)
                        .error(R.drawable.notification_preview_placeholder)
                        .into(binding.ivPreview);
            }
        }

        private CharSequence buildMessage(NotificationItem item) {
            String actor = item.getActor() == null ? "" : item.getActor().trim();
            String message = item.getMessage() == null ? "" : item.getMessage().trim();
            if (actor.isEmpty()) {
                return message;
            }

            String combined = actor + " " + message;
            SpannableStringBuilder builder = new SpannableStringBuilder(combined);
            if (item.isEmphasizeActor()) {
                builder.setSpan(
                        new ForegroundColorSpan(ContextCompat.getColor(
                                binding.getRoot().getContext(),
                                R.color.text_primary
                        )),
                        0,
                        actor.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                builder.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        0,
                        actor.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            return builder;
        }
    }
}
