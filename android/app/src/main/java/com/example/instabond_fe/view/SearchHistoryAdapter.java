package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.SearchHistoryDTO;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private List<SearchHistoryDTO> historyList = new ArrayList<>();
    private final OnHistoryClickListener listener;

    public interface OnHistoryClickListener {
        void onItemClick(SearchHistoryDTO history);
        void onDeleteClick(SearchHistoryDTO history);
    }

    public SearchHistoryAdapter(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setHistoryList(List<SearchHistoryDTO> historyList) {
        this.historyList = historyList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchHistoryDTO history = historyList.get(position);

        if ("PROFILE".equalsIgnoreCase(history.getType())) {
            holder.ivHistoryIcon.setVisibility(View.GONE);
            holder.ivHistoryAvatar.setVisibility(View.VISIBLE);
            holder.tvHistoryText.setText(history.getTargetUsername());
            holder.tvHistoryFullName.setVisibility(View.VISIBLE);
            holder.tvHistoryFullName.setText(history.getTargetFullName() != null ? history.getTargetFullName() : "");

            Glide.with(holder.itemView.getContext())
                    .load(history.getTargetAvatarUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(holder.ivHistoryAvatar);
        } else {
            holder.ivHistoryIcon.setVisibility(View.VISIBLE);
            holder.ivHistoryAvatar.setVisibility(View.GONE);
            holder.tvHistoryText.setText(history.getKeyword());
            holder.tvHistoryFullName.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(history));
        holder.btnDeleteHistory.setOnClickListener(v -> listener.onDeleteClick(history));
    }

    @Override
    public int getItemCount() {
        return historyList == null ? 0 : historyList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivHistoryIcon;
        ImageView ivHistoryAvatar;
        TextView tvHistoryText;
        TextView tvHistoryFullName;
        ImageButton btnDeleteHistory;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivHistoryIcon = itemView.findViewById(R.id.iv_history_icon);
            ivHistoryAvatar = itemView.findViewById(R.id.iv_history_avatar);
            tvHistoryText = itemView.findViewById(R.id.tv_history_text);
            btnDeleteHistory = itemView.findViewById(R.id.btn_delete_history);
            tvHistoryFullName = itemView.findViewById(R.id.tv_history_full_name);
        }
    }
}