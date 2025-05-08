package com.example.drawit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit.R;

import java.util.ArrayList;
import java.util.List;

public class PlayersAdapter extends RecyclerView.Adapter<PlayersAdapter.PlayerViewHolder> {
    private static final String TAG = "PlayersAdapter";
    private final List<String> players;

    public PlayersAdapter(List<String> players) {
        this.players = new ArrayList<>();
        if (players != null) {
            this.players.addAll(players);
        }
    }

    public void updatePlayers(List<String> newPlayers) {
        players.clear();
        if (newPlayers != null) {
            players.addAll(newPlayers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player, parent, false);
        return new PlayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        if (position < 0 || position >= players.size()) {
            Log.w(TAG, "Invalid position: " + position + ", players size: " + players.size());
            return;
        }
        
        String playerName = players.get(position);
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Unknown Player";
        }
        
        // Add null check for holder.playerName
        if (holder.playerName != null) {
            holder.playerName.setText(playerName);
        } else {
            Log.e(TAG, "PlayerViewHolder.playerName is null at position: " + position + ". Check layout file item_player.xml for R.id.playerName.");
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        final TextView playerName;

        PlayerViewHolder(View itemView) {
            super(itemView);
            playerName = itemView.findViewById(R.id.playerName);
            if (playerName == null) {
                // This log helps during development if the ID is incorrect or missing in the layout.
                Log.e(TAG, "TextView with R.id.playerName not found in item_player.xml. PlayerViewHolder.playerName is null.");
            }
        }
    }
}