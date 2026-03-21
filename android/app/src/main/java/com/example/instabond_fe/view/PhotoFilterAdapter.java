package com.example.instabond_fe.view;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ItemCreatePostFilterBinding;

import java.util.ArrayList;
import java.util.List;

public class PhotoFilterAdapter extends RecyclerView.Adapter<PhotoFilterAdapter.FilterViewHolder> {

    public interface OnFilterSelectedListener {
        void onFilterSelected(CreatePostActivity.FilterType filterType);
    }

    public static class FilterPreviewItem {
        private final CreatePostActivity.FilterType filterType;
        private final String label;
        private final Bitmap thumbnail;

        public FilterPreviewItem(CreatePostActivity.FilterType filterType, String label, Bitmap thumbnail) {
            this.filterType = filterType;
            this.label = label;
            this.thumbnail = thumbnail;
        }
    }

    private final List<FilterPreviewItem> items = new ArrayList<>();
    private final OnFilterSelectedListener listener;
    private CreatePostActivity.FilterType selectedFilter = CreatePostActivity.FilterType.NORMAL;

    public PhotoFilterAdapter(OnFilterSelectedListener listener) {
        this.listener = listener;
    }

    public void submitItems(List<FilterPreviewItem> filterItems, CreatePostActivity.FilterType selected) {
        items.clear();
        if (filterItems != null) {
            items.addAll(filterItems);
        }
        selectedFilter = selected;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCreatePostFilterBinding binding = ItemCreatePostFilterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new FilterViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
        FilterPreviewItem item = items.get(position);
        boolean isSelected = item.filterType == selectedFilter;

        holder.binding.tvFilterName.setText(item.label);
        holder.binding.tvFilterName.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isSelected ? R.color.login_bg_start : R.color.login_text_secondary
        ));
        holder.binding.cardThumbnail.setStrokeWidth(isSelected ? dp(holder.itemView, 2) : 0);
        holder.binding.cardThumbnail.setStrokeColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isSelected ? R.color.login_bg_start : android.R.color.transparent
        ));
        holder.binding.cardThumbnail.setCardBackgroundColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isSelected ? android.R.color.white : android.R.color.transparent
        ));

        if (item.thumbnail != null) {
            holder.binding.ivThumbnail.setImageBitmap(item.thumbnail);
        } else {
            holder.binding.ivThumbnail.setImageResource(R.drawable.create_post_filter_placeholder);
        }

        holder.itemView.setOnClickListener(v -> listener.onFilterSelected(item.filterType));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dp(View view, int value) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    static class FilterViewHolder extends RecyclerView.ViewHolder {
        private final ItemCreatePostFilterBinding binding;

        FilterViewHolder(ItemCreatePostFilterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
