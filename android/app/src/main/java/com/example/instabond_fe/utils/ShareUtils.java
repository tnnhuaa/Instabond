package com.example.instabond_fe.utils;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.model.ChatMessageRequest;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.network.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShareUtils {

    public static void showShareBottomSheet(Context context, Post post, ApiService apiService, String currentUserId) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View sheetView = LayoutInflater.from(context).inflate(R.layout.layout_share_bottom_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        EditText etShareMessage = sheetView.findViewById(R.id.et_share_message);

        sheetView.findViewById(R.id.btn_share_external).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "https://instabond.com/post/" + post.getId());
            sendIntent.setType("text/plain");
            context.startActivity(Intent.createChooser(sendIntent, "Share"));
        });

        RecyclerView rvUsers = sheetView.findViewById(R.id.rv_share_users);
        rvUsers.setLayoutManager(new LinearLayoutManager(context));

        if (currentUserId != null) {
            apiService.getFollowing(currentUserId).enqueue(new Callback<List<FollowUserResponse>>() {
                @Override
                public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        ShareUserAdapter shareUserAdapter = new ShareUserAdapter(context, response.body(), post, bottomSheetDialog, apiService, etShareMessage);
                        rvUsers.setAdapter(shareUserAdapter);
                    }
                }
                @Override
                public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {}
            });
        }
        bottomSheetDialog.show();
    }

    private static void sendPostAsMessage(Context context, String targetUserId, Post post, BottomSheetDialog dialog, ApiService apiService, String customText) {
        apiService.getOrCreateDirectConversation(targetUserId).enqueue(new Callback<Conversation>() {
            @Override
            public void onResponse(Call<Conversation> call, Response<Conversation> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String conversationId = response.body().getId();

                    JsonObject postData = new JsonObject();
                    postData.addProperty("postId", post.getId());
                    postData.addProperty("username", post.getUsername());
                    postData.addProperty("imageUrl", post.getImageUrl());
                    postData.addProperty("caption", post.getCaption());

                    ChatMessageRequest msgReq = new ChatMessageRequest(conversationId, postData.toString(), "post_share");

                    apiService.sendTextMessage(msgReq).enqueue(new Callback<ChatMessageResponse>() {
                        @Override
                        public void onResponse(Call<ChatMessageResponse> call, Response<ChatMessageResponse> response) {
                            if (response.isSuccessful()) {
                                if (customText != null && !customText.trim().isEmpty()) {
                                    ChatMessageRequest textReq = new ChatMessageRequest(conversationId, customText.trim(), "text");
                                    apiService.sendTextMessage(textReq).enqueue(new Callback<ChatMessageResponse>() {
                                        @Override
                                        public void onResponse(Call<ChatMessageResponse> call, Response<ChatMessageResponse> r) {}
                                        @Override
                                        public void onFailure(Call<ChatMessageResponse> call, Throwable t) {}
                                    });
                                }
                                Toast.makeText(context, "Sent!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                        }
                        @Override
                        public void onFailure(Call<ChatMessageResponse> call, Throwable t) {}
                    });
                }
            }
            @Override
            public void onFailure(Call<Conversation> call, Throwable t) {}
        });
    }

    static class ShareUserAdapter extends RecyclerView.Adapter<ShareUserAdapter.ViewHolder> {
        private final Context context;
        private final List<FollowUserResponse> users;
        private final Post post;
        private final BottomSheetDialog dialog;
        private final ApiService apiService;
        private final EditText etShareMessage;

        public ShareUserAdapter(Context context, List<FollowUserResponse> users, Post post, BottomSheetDialog dialog, ApiService apiService, EditText etShareMessage) {
            this.context = context;
            this.users = users;
            this.post = post;
            this.dialog = dialog;
            this.apiService = apiService;
            this.etShareMessage = etShareMessage;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_share_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FollowUserResponse user = users.get(position);
            holder.tvUsername.setText(user.getUsername());
            AvatarLoader.load(holder.ivAvatar, user.getAvatarUrl());

            holder.btnSend.setOnClickListener(v -> {
                String typedText = etShareMessage != null ? etShareMessage.getText().toString() : "";
                sendPostAsMessage(context, user.getId(), post, dialog, apiService, typedText);
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvUsername;
            Button btnSend;

            ViewHolder(View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
                btnSend = itemView.findViewById(R.id.btn_send);
            }
        }
    }
}
