package com.example.drawit.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit.R;
import com.example.drawit.models.Lobby;

import java.util.ArrayList;
import java.util.List;

public class LobbyAdapter extends RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder> {
    private static final String TAG = "LobbyAdapter";
    private final List<Lobby> lobbies;
    private final OnLobbyClickListener listener;

    public interface OnLobbyClickListener {
        void onLobbyClick(Lobby lobby);
        void onJoinClick(Lobby lobby);
    }

    public LobbyAdapter(List<Lobby> lobbies, OnLobbyClickListener listener) {
        this.lobbies = new ArrayList<>();
        if (lobbies != null) {
            this.lobbies.addAll(lobbies);
        }
        if (listener == null) {
            Log.w(TAG, "OnLobbyClickListener is null. Click events will not be handled.");
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public LobbyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lobby, parent, false);
            return new LobbyViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error creating view holder", e);
            View emptyView = new View(parent.getContext());
            return new LobbyViewHolder(emptyView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final LobbyViewHolder holder, int position) {
        try {
            if (position < 0 || position >= lobbies.size()) {
                Log.w(TAG, "Invalid position: " + position + ", lobbies size: " + lobbies.size());
                return;
            }
            
            final Lobby lobby = lobbies.get(position);
            if (lobby == null) {
                Log.w(TAG, "Lobby at position " + position + " is null.");
                if (holder.lobbyName != null) holder.lobbyName.setText("Invalid Lobby Data");
                if (holder.playerCount != null) holder.playerCount.setText("");
                if (holder.lockIcon != null) holder.lockIcon.setVisibility(View.GONE);
                if (holder.joinButton != null) holder.joinButton.setOnClickListener(null); 
                holder.itemView.setOnClickListener(null); 
                return;
            }
            
            if (holder.lobbyName != null) {
                String name = "Unknown Lobby";
                if (lobby.getName() != null && !lobby.getName().isEmpty()) {
                    name = lobby.getName();
                }
                holder.lobbyName.setText(name);
            }
            
            if (holder.playerCount != null) {
                holder.playerCount.setText("Players: " + lobby.getPlayersCount());
            }
            
            if (holder.lockIcon != null) {
                holder.lockIcon.setVisibility(lobby.hasPassword() ? View.VISIBLE : View.GONE);
            }
            
            if (holder.joinButton != null) {
                holder.joinButton.setOnClickListener(new JoinButtonClickListener(lobby, listener));
            }

            holder.itemView.setOnClickListener(new ItemViewClickListener(lobby, listener));

        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder at position " + position, e);
        }
    }

    @Override
    public int getItemCount() {
        if (lobbies == null) {
            return 0;
        }
        return lobbies.size();
    }

    public void updateLobbies(List<Lobby> newLobbies) {
        lobbies.clear();
        if (newLobbies != null) {
            lobbies.addAll(newLobbies);
        }
        notifyDataSetChanged();
    }

    static class LobbyViewHolder extends RecyclerView.ViewHolder {
        TextView lobbyName;
        TextView playerCount;
        ImageView lockIcon;
        Button joinButton;

        LobbyViewHolder(View itemView) {
            super(itemView);
            lobbyName = itemView.findViewById(R.id.lobbyName);
            playerCount = itemView.findViewById(R.id.playerCount);
            lockIcon = itemView.findViewById(R.id.lockIcon);
            joinButton = itemView.findViewById(R.id.joinButton); 
        }
    }

    private static class JoinButtonClickListener implements View.OnClickListener {
        private final Lobby lobby;
        private final OnLobbyClickListener listener;

        JoinButtonClickListener(Lobby lobby, OnLobbyClickListener listener) {
            this.lobby = lobby;
            this.listener = listener;
        }

        @Override
        public void onClick(View v) {
            if (listener != null && lobby != null) {
                listener.onJoinClick(lobby);
            }
        }
    }

    private static class ItemViewClickListener implements View.OnClickListener {
        private final Lobby lobby;
        private final OnLobbyClickListener listener;

        ItemViewClickListener(Lobby lobby, OnLobbyClickListener listener) {
            this.lobby = lobby;
            this.listener = listener;
        }

        @Override
        public void onClick(View v) {
            if (listener != null && lobby != null) {
                listener.onLobbyClick(lobby);
            }
        }
    }
}