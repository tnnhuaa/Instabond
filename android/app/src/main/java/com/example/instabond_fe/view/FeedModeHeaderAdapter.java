package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ItemFeedModeHeaderBinding;

public class FeedModeHeaderAdapter extends RecyclerView.Adapter<FeedModeHeaderAdapter.HeaderViewHolder> {

    public interface Listener {
        void onModeSelected(String mode);
    }

    public static final String MODE_FOR_YOU = "for_you";
    public static final String MODE_FOLLOWING = "following";

    private String currentMode = MODE_FOR_YOU;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setCurrentMode(String mode) {
        if (mode == null || mode.equals(currentMode)) {
            return;
        }
        currentMode = mode;
        notifyItemChanged(0);
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedModeHeaderBinding binding = ItemFeedModeHeaderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new HeaderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
        holder.bind(currentMode);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedModeHeaderBinding binding;

        HeaderViewHolder(ItemFeedModeHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.chipFeedForYou.setOnClickListener(v -> {
                if (MODE_FOR_YOU.equals(currentMode)) {
                    return;
                }
                if (listener != null) {
                    listener.onModeSelected(MODE_FOR_YOU);
                }
            });

            binding.chipFeedFollowing.setOnClickListener(v -> {
                if (MODE_FOLLOWING.equals(currentMode)) {
                    return;
                }
                if (listener != null) {
                    listener.onModeSelected(MODE_FOLLOWING);
                }
            });
        }

        void bind(String mode) {
            if (MODE_FOR_YOU.equals(mode)) {
                binding.chipFeedForYou.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
                binding.chipFeedForYou.setTextColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.theme_on_primary));

                binding.chipFeedFollowing.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
                binding.chipFeedFollowing.setTextColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.theme_on_surface_muted));
                return;
            }

            binding.chipFeedFollowing.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
            binding.chipFeedFollowing.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(), R.color.theme_on_primary));

            binding.chipFeedForYou.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
            binding.chipFeedForYou.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(), R.color.theme_on_surface_muted));
        }
    }
}
