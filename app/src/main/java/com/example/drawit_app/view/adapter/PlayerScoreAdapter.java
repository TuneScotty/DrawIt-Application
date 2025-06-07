package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.databinding.ItemPlayerScoreBinding;
import com.example.drawit_app.model.PlayerScore;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Adapter for displaying player scores during a game
 */
public class PlayerScoreAdapter extends RecyclerView.Adapter<PlayerScoreAdapter.ScoreViewHolder> {

    private List<PlayerScore> playerScores;

    public PlayerScoreAdapter(List<PlayerScore> playerScores) {
        this.playerScores = playerScores;
    }

    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerScoreBinding binding = ItemPlayerScoreBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ScoreViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        PlayerScore playerScore = playerScores.get(position);
        holder.bind(playerScore, position + 1);
    }

    @Override
    public int getItemCount() {
        return playerScores.size();
    }

    public void updateScores(List<PlayerScore> newScores) {
        newScores.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        this.playerScores = newScores;
        notifyDataSetChanged();
    }

    public static class ScoreViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlayerScoreBinding binding;

        public ScoreViewHolder(ItemPlayerScoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(PlayerScore playerScore, int position) {
            // Set rank and username
            binding.tvRank.setText(String.valueOf(position));
            binding.tvPlayerName.setText(playerScore.getPlayer().getUsername());
            
            // Set score
            binding.tvScore.setText(String.valueOf(playerScore.getScore()));
            
            // Highlight if this player has guessed correctly in the current round
            binding.cardPlayerScore.setCardElevation(
                    playerScore.hasGuessedCurrentRound() ? 8.0f : 2.0f);
        }
    }
}
