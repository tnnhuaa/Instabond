package com.example.instabond_fe.view;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.model.PostSearchDTO;

import java.util.ArrayList;
import java.util.List;

public class SearchPostAdapter extends RecyclerView.Adapter<SearchPostAdapter.PostViewHolder> {

    private List<PostSearchDTO> postList = new ArrayList<>();

    public void setPosts(List<PostSearchDTO> posts) {
        this.postList = posts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_grid_image, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        PostSearchDTO post = postList.get(position);

        if (post.getThumbnailUrl() != null && !post.getThumbnailUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(post.getThumbnailUrl())
                    .centerCrop()
                    .into(holder.ivThumbnail);
        }

        // EVENT: Click on post thumbnail to navigate to post details
        holder.itemView.setOnClickListener(v -> {
            if (post.getId() == null || post.getId().trim().isEmpty()) {
                return;
            }

            Intent intent = new Intent(v.getContext(), CommentActivity.class);
            intent.putExtra("postId", post.getId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return postList == null ? 0 : postList.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_grid_thumbnail);
        }
    }
}
