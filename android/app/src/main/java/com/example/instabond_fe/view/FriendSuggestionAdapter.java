package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendSuggestionAdapter extends RecyclerView.Adapter<FriendSuggestionAdapter.SuggestionViewHolder> {
    private final List<FollowUserResponse> suggestions;
    private final Context context;
    private final ApiService apiService;

    public FriendSuggestionAdapter(List<FollowUserResponse> suggestions, Context context) {
        this.suggestions = suggestions;
        this.context = context;
        this.apiService = ApiClient.getApiService(context);
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_suggestion, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        FollowUserResponse suggestion = suggestions.get(position);

        holder.tvUsername.setText(suggestion.getUsername());
        holder.tvName.setText(suggestion.getFullName() != null ? suggestion.getFullName() : "");

        Glide.with(context)
                .load(suggestion.getAvatarUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);

        holder.btnFollow.setOnClickListener(v -> followUser(suggestion.getId()));
        holder.itemView.setOnClickListener(v -> viewProfile(suggestion.getId()));
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    private void followUser(String userId) {
        apiService.followUser(userId).enqueue(new Callback<FollowUserResponse>() {
            @Override
            public void onResponse(Call<FollowUserResponse> call, Response<FollowUserResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(context, "Đã theo dõi", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FollowUserResponse> call, Throwable t) {
                Toast.makeText(context, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void viewProfile(String userId) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra("targetUserId", userId);
        context.startActivity(intent);
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        TextView tvName;
        Button btnFollow;

        SuggestionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvName = itemView.findViewById(R.id.tv_name);
            btnFollow = itemView.findViewById(R.id.btn_follow);
        }
    }
}


