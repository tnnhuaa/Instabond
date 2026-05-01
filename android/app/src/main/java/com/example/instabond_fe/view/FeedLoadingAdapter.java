package com.example.instabond_fe.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.instabond_fe.R;

public class FeedLoadingAdapter extends RecyclerView.Adapter<FeedLoadingAdapter.LoadingViewHolder> {

    private boolean isLoading = false;

    public void setLoading(boolean isLoading) {
        if (this.isLoading != isLoading) {
            this.isLoading = isLoading;
            if (isLoading) {
                notifyItemInserted(0);
            } else {
                notifyItemRemoved(0);
            }
        }
    }

    @NonNull
    @Override
    public LoadingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_loading, parent, false);
        return new LoadingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LoadingViewHolder holder, int position) {
        // No binding needed
    }

    @Override
    public int getItemCount() {
        return isLoading ? 1 : 0;
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}