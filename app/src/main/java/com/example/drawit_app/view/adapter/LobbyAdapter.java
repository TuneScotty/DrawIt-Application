package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.databinding.ItemLobbyBinding;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;

import java.util.List;

/**
 * Adapter for displaying a list of game lobbies
 */
public class LobbyAdapter extends RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder> {

    private List<Lobby> lobbies;
    private final LobbyClickListener listener;

    public LobbyAdapter(List<Lobby> lobbies, LobbyClickListener listener) {
        this.lobbies = lobbies;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LobbyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLobbyBinding binding = ItemLobbyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LobbyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LobbyViewHolder holder, int position) {
        Lobby lobby = lobbies.get(position);
        System.out.println("Binding lobby: " + lobby);
        holder.bind(lobby);
    }

    @Override
    public int getItemCount() {
        return lobbies.size();
    }

    public void updateLobbies(List<Lobby> newLobbies) {
        this.lobbies = newLobbies;
        notifyDataSetChanged();
    }

    public class LobbyViewHolder extends RecyclerView.ViewHolder {
        private final ItemLobbyBinding binding;

        public LobbyViewHolder(ItemLobbyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Lobby lobby) {
            // Check for null lobby early to prevent crashes
            if (lobby == null) {
                android.util.Log.e("LobbyAdapter", "Cannot bind null lobby");
                binding.tvLobbyName.setText("Unknown Lobby");
                binding.tvHostName.setText("Host: Unknown");
                binding.tvPlayerCount.setText("Players: 0/0");
                binding.tvRounds.setText("Rounds: 0");
                binding.tvRoundDuration.setText("Duration: 0s");
                binding.iconLock.setVisibility(android.view.View.GONE);
                return;
            }
            
            try {
                // Set lobby name with null check
                String lobbyName = lobby.getName();
                if (lobbyName == null || lobbyName.isEmpty()) {
                    lobbyName = "Unnamed Lobby";
                }
                binding.tvLobbyName.setText(lobbyName);
                
                // Get actual host data using the Lobby's getHost method
                User host = lobby.getHost();
                
                // Debug log to check host details
                if (host != null) {
                    android.util.Log.d("LobbyAdapter", "Host found: ID=" + host.getUserId() + ", Username=" + host.getUsername());
                } else {
                    android.util.Log.d("LobbyAdapter", "Host is null for lobby: " + lobby.getLobbyId());
                }
                
                // Use the actual host information or a fallback
                String hostUsername = "";
                if (host != null) {
                    if (host.getUsername() != null && !host.getUsername().isEmpty()) {
                        // Use the actual username
                        hostUsername = host.getUsername();
                        android.util.Log.d("LobbyAdapter", "Using actual host username: " + hostUsername);
                    } else {
                        // Host found but no username, use ID-based fallback
                        String hostId = host.getUserId();
                        if (hostId != null && !hostId.isEmpty()) {
                            hostUsername = "Player-" + hostId.substring(0, Math.min(4, hostId.length()));
                            android.util.Log.d("LobbyAdapter", "Host found but no username, using ID-based: " + hostUsername);
                        } else {
                            hostUsername = "Unknown Host";
                            android.util.Log.d("LobbyAdapter", "Host found but no ID either, using 'Unknown Host'");
                        }
                    }
                } else if (lobby.getHostId() != null && !lobby.getHostId().isEmpty()) {
                    // No host found but we have hostId
                    hostUsername = "Player-" + lobby.getHostId().substring(0, Math.min(4, lobby.getHostId().length()));
                    android.util.Log.d("LobbyAdapter", "No host found, using ID-based: " + hostUsername);
                } else {
                    // No host information at all
                    hostUsername = "Unknown Host";
                    android.util.Log.d("LobbyAdapter", "No host information at all, using 'Unknown Host'");
                }
                
                // Set host label correctly - JUST the username, not role
                binding.tvHostName.setText("Host: " + hostUsername);
                
                // Calculate ACCURATE player count - count the actual number of player objects
                int actualPlayerCount = 0;
                if (lobby.getPlayers() != null) {
                    actualPlayerCount = lobby.getPlayers().size();
                    // Log all players for debugging
                    StringBuilder playerLog = new StringBuilder("Players in lobby " + lobby.getLobbyId() + ": ");
                    for (User player : lobby.getPlayers()) {
                        String playerName = player.getUsername() != null ? player.getUsername() : "Unknown";
                        String playerId = player.getUserId() != null ? player.getUserId() : "Unknown";
                        playerLog.append(playerName).append("(").append(playerId).append("), ");
                    }
                    android.util.Log.d("LobbyAdapter", playerLog.toString());
                }
                
                // Ensure we always show the player count accurately, even if zero
                binding.tvPlayerCount.setText("Players: " + actualPlayerCount + "/" + lobby.getMaxPlayers());
                
                // Get ACTUAL round settings from server data
                // First try the getNumRounds method
                int rounds = lobby.getNumRounds();
                // Fallback to getRounds if it seems like a default value
                if (rounds <= 0) {
                    rounds = lobby.getRounds();
                }
                android.util.Log.d("LobbyAdapter", "Displaying rounds: " + rounds);
                binding.tvRounds.setText("Rounds: " + rounds);
                
                // Get ACTUAL duration from server data
                // First try getRoundDurationSeconds method
                int duration = lobby.getRoundDurationSeconds();
                // Fallback to getRoundDuration if it seems like a default value
                if (duration <= 0) {
                    duration = lobby.getRoundDuration();
                }
                android.util.Log.d("LobbyAdapter", "Displaying duration: " + duration + "s");
                binding.tvRoundDuration.setText("Duration: " + duration + "s");
                
                // Show lock icon if lobby is locked
                binding.iconLock.setVisibility(lobby.isLocked() ? View.VISIBLE : View.GONE);
                
                // Set click listener on join button
                binding.btnJoinLobby.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLobbyClick(lobby);
                    }
                });
                
                // Also set click listener for the entire item
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLobbyClick(lobby);
                    }
                });
                
                // Comprehensive debug log of the exact lobby data we're displaying
                android.util.Log.d("LobbyAdapter", "Full lobby data - ID: " + lobby.getLobbyId() + 
                        ", Name: " + lobbyName + 
                        ", Host: " + hostUsername + " (ID: " + lobby.getHostId() + ")" +
                        ", Players: " + actualPlayerCount + "/" + lobby.getMaxPlayers() + 
                        ", Rounds: " + rounds + 
                        ", Duration: " + duration + "s");
            } catch (Exception e) {
                // Robust error handling to prevent crashes
                android.util.Log.e("LobbyAdapter", "Error binding lobby data", e);
            }
        }
    }

    public interface LobbyClickListener {
        void onLobbyClick(Lobby lobby);
    }
}
