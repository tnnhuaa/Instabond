package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.SuggestedTag;
import com.example.instabond_fe.utils.AvatarLoader;
import java.util.ArrayList;
import java.util.List;

public class TaggedUserAdapter extends RecyclerView.Adapter<TaggedUserAdapter.ViewHolder> {

    private List<SuggestedTag> taggedUsers = new ArrayList<>();
    private final boolean isRemovable;
    private OnRemoveClickListener removeListener;

    public interface OnRemoveClickListener {
        void onRemove(SuggestedTag tag, int position);
    }

    public TaggedUserAdapter(boolean isRemovable) {
        this.isRemovable = isRemovable;
    }

    public void setOnRemoveClickListener(OnRemoveClickListener listener) {
        this.removeListener = listener;
    }

    public void setTaggedUsers(List<SuggestedTag> users) {
        this.taggedUsers = users;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tagged_user, parent, false);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!isRemovable) {
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        view.setLayoutParams(layoutParams);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SuggestedTag user = taggedUsers.get(position);
        holder.tvUsername.setText(user.getUsername());
        holder.tvFullname.setText(user.getFullName() != null ? user.getFullName() : "");
        AvatarLoader.load(holder.ivAvatar, user.getAvatarUrl());

        // Logic remove tag if isRemovable = true
        /*
        if (isRemovable) {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> {
                if (removeListener != null) removeListener.onRemove(user, position);
            });
        } else {
            holder.btnRemove.setVisibility(View.GONE);
        }
        */
    }

    @Override
    public int getItemCount() {
        return taggedUsers == null ? 0 : taggedUsers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvUsername;
        TextView tvFullname;
        // ImageButton btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvFullname = itemView.findViewById(R.id.tv_fullname);
            // btnRemove = itemView.findViewById(R.id.btn_remove_tag);
        }
    }
}