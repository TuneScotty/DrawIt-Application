package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.ItemGameResultBinding;
import com.example.drawit_app.model.PlayerScore;

import java.util.List;

/**
 * Adapter for displaying final game results and player rankings
 */
public class GameResultsAdapter extends RecyclerView.Adapter<GameResultsAdapter.ResultViewHolder> {

    private List<PlayerScore> results;

    public GameResultsAdapter(List<PlayerScore> results) {
        this.results = results;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGameResultBinding binding = ItemGameResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        PlayerScore result = results.get(position);
        holder.bind(result, position);
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void updateResults(List<PlayerScore> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    public static class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemGameResultBinding binding;

        public ResultViewHolder(ItemGameResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(PlayerScore result, int position) {
            // Set rank and username
            int rank = position + 1;
            binding.tvRank.setText(String.valueOf(rank));
            binding.tvPlayerName.setText(result.getPlayer().getUsername());
            
            // Set score
            binding.tvScore.setText(String.valueOf(result.getScore()));
            
            // Highlight top 3 positions with medals
            if (rank <= 3) {
                binding.cardResult.setCardBackgroundColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), getMedalColor(rank)));
                binding.ivMedal.setImageResource(getMedalIcon(rank));
                binding.ivMedal.setVisibility(ViewGroup.VISIBLE);
            } else {
                binding.cardResult.setCardBackgroundColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorBackground));
                binding.ivMedal.setVisibility(ViewGroup.GONE);
            }
            
            // Set statistics if available
            if (result.getCorrectGuesses() > 0) {
                binding.tvCorrectGuesses.setText(String.valueOf(result.getCorrectGuesses()));
            }
            
            if (result.getDrawingRatings() > 0) {
                float avgRating = result.getTotalRatingPoints() / (float) result.getDrawingRatings();
                binding.tvDrawingRating.setText(String.format("%.1f", avgRating));
            }
        }
        
        private int getMedalColor(int rank) {
            switch (rank) {
                case 1:
                    return R.color.colorGold;
                case 2:
                    return R.color.colorSilver;
                case 3:
                    return R.color.colorBronze;
                default:
                    return R.color.colorBackground;
            }
        }
        
        private int getMedalIcon(int rank) {
            switch (rank) {
                case 1:
                    return R.drawable.ic_medal_gold;
                case 2:
                    return R.drawable.ic_medal_silver;
                case 3:
                    return R.drawable.ic_medal_bronze;
                default:
                    return 0;
            }
        }
    }
}
