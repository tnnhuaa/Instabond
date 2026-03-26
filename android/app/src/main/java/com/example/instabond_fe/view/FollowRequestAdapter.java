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

public class FollowRequestAdapter extends RecyclerView.Adapter<FollowRequestAdapter.ViewHolder> {

    private List<FollowUserResponse> requests = new ArrayList<>();
    private final OnRequestActionListener listener;

    public interface OnRequestActionListener {
        void onAccept(FollowUserResponse user, int position);
        void onReject(FollowUserResponse user, int position);
    }

    public FollowRequestAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FollowUserResponse> requests) {
        this.requests = requests;
        notifyDataSetChanged();
    }

    public void removeRequest(int position) {
        requests.remove(position);
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_follow_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FollowUserResponse user = requests.get(position);
        holder.tvUsername.setText(user.getUsername());
        holder.tvFullname.setText(user.getFullName());
        Glide.with(holder.itemView.getContext())
                .load(user.getAvatarUrl())
                .placeholder(R.drawable.profile_placeholder_bg)
                .into(holder.ivAvatar);

        holder.btnAccept.setOnClickListener(v -> listener.onAccept(user, position));
        holder.btnReject.setOnClickListener(v -> listener.onReject(user, position));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        TextView tvFullname;
        Button btnAccept;
        Button btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvFullname = itemView.findViewById(R.id.tv_fullname);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }
    }
}