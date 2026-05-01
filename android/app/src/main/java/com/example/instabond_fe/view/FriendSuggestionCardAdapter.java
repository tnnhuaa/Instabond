package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.utils.AvatarLoader;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendSuggestionCardAdapter extends RecyclerView.Adapter<FriendSuggestionCardAdapter.ViewHolder> {

    private final List<FollowUserResponse> suggestions;
    private final Context context;
    private final ApiService apiService;
    private final com.example.instabond_fe.network.SessionManager sessionManager;
    private OnFollowListener listener;

    private enum FollowButtonState {
        FOLLOW,
        PROCESSING,
        FOLLOWING
    }

    public interface OnFollowListener {
        void onFollowSuccess(FollowUserResponse user, int position);
    }

    public FriendSuggestionCardAdapter(List<FollowUserResponse> suggestions, Context context, ApiService apiService) {
        this.suggestions = new ArrayList<>();
        if (suggestions != null) {
            this.suggestions.addAll(suggestions);
        }
        this.context = context;
        this.apiService = apiService;
        this.sessionManager = new com.example.instabond_fe.network.SessionManager(context);
    }

    public void setListener(OnFollowListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_suggestion_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FollowUserResponse user = suggestions.get(position);

        AvatarLoader.load(holder.ivAvatar, user.getAvatarUrl());
        holder.tvUsername.setText(user.getUsername());
        holder.btnFollow.setEnabled(true);
        holder.btnFollow.setAlpha(1f);
        applyFollowButtonState(holder, FollowButtonState.FOLLOW);

        holder.btnFollow.setOnClickListener(v -> followUser(user, position, holder));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ProfileActivity.class);
            intent.putExtra("targetUserId", user.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return suggestions != null ? suggestions.size() : 0;
    }

    public void submitSuggestions(List<FollowUserResponse> newSuggestions) {
        suggestions.clear();
        if (newSuggestions != null) {
            suggestions.addAll(newSuggestions);
        }
        notifyDataSetChanged();
    }

    private void followUser(FollowUserResponse user, int position, ViewHolder holder) {
        applyFollowButtonState(holder, FollowButtonState.PROCESSING);
        apiService.followUser(user.getId()).enqueue(new Callback<FollowUserResponse>() {
            @Override
            public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(context, "Đã theo dõi", Toast.LENGTH_SHORT).show();
                    applyFollowButtonState(holder, FollowButtonState.FOLLOWING);
                    if (listener != null) {
                        listener.onFollowSuccess(user, position);
                    }
                } else {
                    applyFollowButtonState(holder, FollowButtonState.FOLLOW);
                    if (sessionManager.isLoggedIn()) {
                        Toast.makeText(context, "Theo dõi thất bại", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                applyFollowButtonState(holder, FollowButtonState.FOLLOW);
                if (sessionManager.isLoggedIn()) {
                    Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void applyFollowButtonState(ViewHolder holder, FollowButtonState state) {
        if (state == FollowButtonState.FOLLOW) {
            holder.btnFollow.setEnabled(true);
            holder.btnFollow.setAlpha(1f);
            holder.btnFollow.setText(R.string.profile_action_follow);
            holder.btnFollow.setBackgroundResource(R.drawable.search_follow_button_bg);
            holder.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.login_primary_text));
            return;
        }

        if (state == FollowButtonState.PROCESSING) {
            holder.btnFollow.setEnabled(false);
            holder.btnFollow.setAlpha(0.75f);
            holder.btnFollow.setText(R.string.profile_action_follow_processing);
            holder.btnFollow.setBackgroundResource(R.drawable.search_follow_button_bg);
            holder.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.login_primary_text));
            return;
        }

        holder.btnFollow.setEnabled(false);
        holder.btnFollow.setAlpha(1f);
        holder.btnFollow.setText(R.string.profile_action_following);
        holder.btnFollow.setBackgroundResource(R.drawable.search_follow_back_button_bg);
        holder.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.login_text_primary));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        Button btnFollow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            btnFollow = itemView.findViewById(R.id.btn_follow);
        }
    }
}

