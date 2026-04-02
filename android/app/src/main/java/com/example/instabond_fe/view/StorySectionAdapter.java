package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.databinding.ItemStoryFeedSectionBinding;
import com.example.instabond_fe.model.StoryItem;

import java.util.ArrayList;
import java.util.List;

public class StorySectionAdapter extends RecyclerView.Adapter<StorySectionAdapter.SectionViewHolder> {

    private final StoryFeedAdapter storyFeedAdapter;
    private final List<StoryItem> items = new ArrayList<>();

    public StorySectionAdapter(StoryFeedAdapter storyFeedAdapter) {
        this.storyFeedAdapter = storyFeedAdapter;
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
    public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStoryFeedSectionBinding binding = ItemStoryFeedSectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SectionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
        holder.bind(items);
    }

    @Override
    public int getItemCount() {
        return items.isEmpty() ? 0 : 1;
    }

    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemStoryFeedSectionBinding binding;

        SectionViewHolder(ItemStoryFeedSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.rvStorySection.setLayoutManager(
                    new LinearLayoutManager(binding.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false));
            binding.rvStorySection.setAdapter(storyFeedAdapter);
        }

        void bind(List<StoryItem> storyItems) {
            storyFeedAdapter.submitItems(storyItems);
        }
    }
}
