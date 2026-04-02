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

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendSuggestionCardAdapter extends RecyclerView.Adapter<FriendSuggestionCardAdapter.ViewHolder> {

    private List<FollowUserResponse> suggestions;
    private Context context;
    private ApiService apiService;
    private OnFollowListener listener;

    public interface OnFollowListener {
        void onFollowSuccess(FollowUserResponse user, int position);
    }

    public FriendSuggestionCardAdapter(List<FollowUserResponse> suggestions, Context context, ApiService apiService) {
        this.suggestions = suggestions;
        this.context = context;
        this.apiService = apiService;
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
        holder.btnFollow.setText(R.string.profile_action_follow);
        holder.btnFollow.setBackgroundResource(R.drawable.search_follow_button_bg);
        holder.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.login_text_primary));

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

    private void followUser(FollowUserResponse user, int position, ViewHolder holder) {
        holder.btnFollow.setEnabled(false);
        apiService.followUser(user.getId()).enqueue(new Callback<FollowUserResponse>() {
            @Override
            public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                holder.btnFollow.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(context, "Đã theo dõi", Toast.LENGTH_SHORT).show();
                    holder.btnFollow.setEnabled(false);
                    holder.btnFollow.setAlpha(1f);
                    holder.btnFollow.setText(R.string.profile_action_following);
                    holder.btnFollow.setBackgroundResource(R.drawable.search_follow_back_button_bg);
                    holder.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.login_text_primary));
                    if (listener != null) {
                        listener.onFollowSuccess(user, position);
                    }
                } else {
                    Toast.makeText(context, "Theo dõi thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                holder.btnFollow.setEnabled(true);
                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
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

