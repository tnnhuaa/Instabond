package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.PostResponse;

import java.util.ArrayList;
import java.util.List;

public class ProfileGridAdapter extends RecyclerView.Adapter<ProfileGridAdapter.GridViewHolder> {

    public interface OnGridItemClickListener {
        // Trả về danh sách bài đăng và vị trí bài được click để lát nữa truyền sang màn hình cuộn
        void onGridItemClick(List<PostResponse> allPosts, int clickedPosition);
    }

    private final List<PostResponse> posts = new ArrayList<>();
    private OnGridItemClickListener listener;

    public void setPosts(List<PostResponse> newPosts) {
        posts.clear();
        if (newPosts != null) {
            posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }

    public void setListener(OnGridItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        PostResponse post = posts.get(position);

        // Lấy URL ảnh đầu tiên của bài viết (nếu có)
        String imageUrl = null;
        if (post.getMedia() != null && !post.getMedia().isEmpty()) {
            imageUrl = post.getMedia().get(0).getUrl();
        }

        Glide.with(holder.itemView)
                .load(imageUrl)
                .placeholder(R.drawable.avatar_circle_bg) // Có thể đổi thành màu xám đen
                .into(holder.ivPhoto);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGridItemClick(posts, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_grid_photo);
        }
    }
}