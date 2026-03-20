package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.FollowUserResponse;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    public interface OnUserInteractionListener {
        void onUserClicked(FollowUserResponse user);
        void onActionClicked(FollowUserResponse user, int position);
        void onUserLongClicked(FollowUserResponse user, int position);
    }

    private final List<FollowUserResponse> users = new ArrayList<>();
    private OnUserInteractionListener listener;
    private String profileOwnerId;
    private String currentUserId;

    public void setUsers(List<FollowUserResponse> newUsers) {
        users.clear();
        if (newUsers != null) {
            users.addAll(newUsers);
        }
        notifyDataSetChanged();
    }
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = (currentUserId != null) ? currentUserId.trim() : null;
    }
    public void setProfileOwnerId(String profileOwnerId) {
        this.profileOwnerId = (profileOwnerId != null) ? profileOwnerId.trim() : null;
    }

    public void setListener(OnUserInteractionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        FollowUserResponse user = users.get(position);
        holder.tvUsername.setText(user.getUsername() != null ? user.getUsername() : "unknown");
        holder.tvFullname.setText(user.getFullName() != null ? user.getFullName() : "");
        if (holder.ivStar != null) {
            holder.ivStar.setVisibility(user.isCloseFriend() ? View.VISIBLE : View.GONE);
        }
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onUserLongClicked(user, position);
            }
            return true;
        });
        Glide.with(holder.itemView)
                .load(user.getAvatarUrl())
                .placeholder(R.drawable.avatar_circle_bg)
                .error(R.drawable.avatar_circle_bg)
                .into(holder.ivAvatar);

        String itemId = user.getId();
        if (itemId != null) itemId = itemId.trim();

        // Kiểm tra xem user trong list có phải là chính mình không
        boolean isMe = itemId != null && currentUserId != null && itemId.equalsIgnoreCase(currentUserId);

        // Luôn đảm bảo nút hiển thị ban đầu để tránh lỗi tái sử dụng View (Recycle)
        holder.btnAction.setVisibility(View.VISIBLE);

        if (isMe) {
            // Nếu là tài khoản của mình: Hiện chữ "Bạn" và không cho bấm
            holder.btnAction.setText("Bạn");
            holder.btnAction.setEnabled(false);
            holder.btnAction.setAlpha(0.6f);
            holder.btnAction.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            // Nếu là người khác: Trạng thái nút phụ thuộc vào relationship status
            holder.btnAction.setEnabled(true);
            holder.btnAction.setAlpha(1.0f);

            boolean isFollowed = user.isMutualFollow() || "accepted".equals(user.getRelationshipStatus());

            if (isFollowed) {
                holder.btnAction.setText("Đang theo dõi");
                holder.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_follow_minus, 0, 0, 0);
            } else if ("pending".equals(user.getRelationshipStatus())) {
                holder.btnAction.setText("Đã yêu cầu");
                holder.btnAction.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            } else {
                holder.btnAction.setText("Theo dõi");
                holder.btnAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_follow_plus, 0, 0, 0);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onUserClicked(user);
        });

        holder.btnAction.setOnClickListener(v -> {
            if (listener != null) listener.onActionClicked(user, position);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivStar;
        TextView tvUsername;
        TextView tvFullname;
        Button btnAction;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvFullname = itemView.findViewById(R.id.tv_fullname);
            btnAction = itemView.findViewById(R.id.btn_action);
            ivStar = itemView.findViewById(R.id.iv_close_friend_star);
        }
    }
}
