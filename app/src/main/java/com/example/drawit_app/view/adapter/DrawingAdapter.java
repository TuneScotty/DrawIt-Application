package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.databinding.ItemDrawingBinding;
import com.example.drawit_app.model.Drawing;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying a grid of drawings in the archive
 */
public class DrawingAdapter extends RecyclerView.Adapter<DrawingAdapter.DrawingViewHolder> {

    private List<Drawing> drawings;
    private final DrawingClickListener listener;
    private final SimpleDateFormat dateFormat;

    public DrawingAdapter(List<Drawing> drawings, DrawingClickListener listener) {
        this.drawings = drawings;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public DrawingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDrawingBinding binding = ItemDrawingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DrawingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DrawingViewHolder holder, int position) {
        Drawing drawing = drawings.get(position);
        holder.bind(drawing);
    }

    @Override
    public int getItemCount() {
        return drawings.size();
    }

    public void updateDrawings(List<Drawing> newDrawings) {
        this.drawings = newDrawings;
        notifyDataSetChanged();
    }

    public class DrawingViewHolder extends RecyclerView.ViewHolder {
        private final ItemDrawingBinding binding;

        public DrawingViewHolder(ItemDrawingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Drawing drawing) {
            // Set basic info
            binding.tvWord.setText(drawing.getWord());
            binding.tvArtist.setText(drawing.getArtist().getUsername());
            binding.tvDate.setText(dateFormat.format(drawing.getCreatedAt()));
            
            // Format rating
            String ratingText = String.format(Locale.getDefault(), "%.1f", drawing.getAverageRating());
            binding.tvRating.setText(ratingText);
            
            // Load drawing preview
            if (drawing.getImagePath() != null) {
                // Set the image from the drawable resource or use a placeholder
                binding.ivDrawingThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                // In a real implementation, you would load the image from a file or URL
                // using Glide or similar library
            }
            
            // Set click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDrawingClick(drawing);
                }
            });
        }
    }

    public interface DrawingClickListener {
        void onDrawingClick(Drawing drawing);
    }
}
