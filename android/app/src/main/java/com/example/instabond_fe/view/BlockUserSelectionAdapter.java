package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.utils.AvatarLoader;

import java.util.List;

public class BlockUserSelectionAdapter extends RecyclerView.Adapter<BlockUserSelectionAdapter.ViewHolder> {

    private List<FollowUserResponse> users;
    private OnUserSelectListener listener;

    public interface OnUserSelectListener {
        void onUserSelected(FollowUserResponse user);
    }

    public BlockUserSelectionAdapter(List<FollowUserResponse> users, OnUserSelectListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_user_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FollowUserResponse user = users.get(position);

        AvatarLoader.load(holder.ivAvatar, user.getAvatarUrl());
        holder.tvUsername.setText(user.getUsername());
        holder.tvFullName.setText(user.getFullName());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserSelected(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users != null ? users.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        TextView tvFullName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvFullName = itemView.findViewById(R.id.tv_fullname);
        }
    }
}

