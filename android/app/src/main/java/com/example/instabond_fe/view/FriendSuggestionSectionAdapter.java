package com.example.instabond_fe.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.databinding.ItemFriendSuggestionSectionBinding;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.network.ApiService;

import java.util.ArrayList;
import java.util.List;

public class FriendSuggestionSectionAdapter extends RecyclerView.Adapter<FriendSuggestionSectionAdapter.SectionViewHolder> {

    public interface OnDismissListener {
        void onDismissed();
    }

    private final Context context;
    private final ApiService apiService;
    private final List<FollowUserResponse> suggestions = new ArrayList<>();
    private boolean dismissed;
    private OnDismissListener dismissListener;

    public FriendSuggestionSectionAdapter(Context context, ApiService apiService) {
        this.context = context;
        this.apiService = apiService;
    }

    public void setOnDismissListener(OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
    }

    public void submitSuggestions(List<FollowUserResponse> items) {
        suggestions.clear();
        if (items != null) {
            suggestions.addAll(items);
        }
        notifyDataSetChanged();
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void dismissSection() {
        if (dismissed) {
            return;
        }
        boolean wasVisible = getItemCount() > 0;
        dismissed = true;
        if (wasVisible) {
            notifyItemRemoved(0);
        }
        if (dismissListener != null) {
            dismissListener.onDismissed();
        }
    }

    @NonNull
    @Override
    public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFriendSuggestionSectionBinding binding = ItemFriendSuggestionSectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SectionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
        holder.bind(suggestions);
    }

    @Override
    public int getItemCount() {
        return dismissed || suggestions.isEmpty() ? 0 : 1;
    }

    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendSuggestionSectionBinding binding;
        private final FriendSuggestionCardAdapter adapter;

        SectionViewHolder(ItemFriendSuggestionSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.adapter = new FriendSuggestionCardAdapter(new ArrayList<>(), context, apiService);
            binding.rvInlineFriendSuggestions.setLayoutManager(
                    new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            binding.rvInlineFriendSuggestions.setAdapter(adapter);
            binding.btnDismissSuggestions.setOnClickListener(v -> dismissSection());
        }

        void bind(List<FollowUserResponse> items) {
            adapter.submitSuggestions(items);
        }
    }
}
