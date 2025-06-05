package com.example.drawit_app.view.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.ItemPlayerBinding;
import com.example.drawit_app.model.User;
import com.example.drawit_app.repository.BaseRepository;
import com.example.drawit_app.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying a list of players in a lobby
 */
public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    private List<User> players;
    private String hostUsername;
    private UserRepository userRepository;
    private LifecycleOwner lifecycleOwner;

    public PlayerAdapter(List<User> players) {
        this.players = players != null ? players : new ArrayList<>();
    }
    
    public PlayerAdapter(List<User> players, UserRepository userRepository, LifecycleOwner lifecycleOwner) {
        this.players = players != null ? players : new ArrayList<>();
        this.userRepository = userRepository;
        this.lifecycleOwner = lifecycleOwner;
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerBinding binding = ItemPlayerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PlayerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        User player = players.get(position);
        holder.bind(player);
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    /**
     * Update the player list with new data
     * This method creates a deep copy of the player list to avoid reference issues
     * and logs detailed information about the update for debugging purposes
     * 
     * @param newPlayers The new list of players
     */
    public void updatePlayers(List<User> newPlayers) {
        Log.d("PlayerAdapter", "Updating player list - old size: " + players.size() + 
              ", new size: " + (newPlayers != null ? newPlayers.size() : 0));
        
        if (newPlayers == null) {
            Log.w("PlayerAdapter", "Received null player list, clearing players");
            this.players.clear();
        } else {
            // Log each player for debugging
            for (User player : newPlayers) {
                Log.d("PlayerAdapter", "Player in new list: " + 
                    (player.getUsername() != null ? player.getUsername() : "null") + 
                    ", ID: " + (player.getUserId() != null ? player.getUserId() : "null"));
            }
            
            // Create a new copy to avoid reference issues
            this.players = new ArrayList<>(newPlayers);
        }
        
        // Force a complete redraw of the list
        notifyDataSetChanged();
        
        Log.d("PlayerAdapter", "Player list updated, new size: " + players.size());
    }
    
    public void setHostUsername(String hostUsername) {
        this.hostUsername = hostUsername;
        notifyDataSetChanged();
    }

    public class PlayerViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlayerBinding binding;

        public PlayerViewHolder(ItemPlayerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User player) {
            if (player == null) {
                binding.tvPlayerName.setText("Unknown Player");
                return;
            }
            
            // Only show actual username - never show user IDs
            String username = "Unknown Player";
            
            // Check if we already have a username
            if (player.getUsername() != null && !player.getUsername().isEmpty()) {
                username = player.getUsername();
            }
            
            // Set the name
            binding.tvPlayerName.setText(username);
            
            // If we still don't have a username and we have the repository, try to fetch it
            if (username.equals("Unknown Player") && 
                userRepository != null && 
                lifecycleOwner != null && 
                player.getUserId() != null && 
                !player.getUserId().isEmpty()) {
                
                // Try to get the name from the repository
                userRepository.getUserById(player.getUserId()).observe(lifecycleOwner, resource -> {
                    if (resource.getStatus() == BaseRepository.Resource.Status.SUCCESS && 
                        resource.getData() != null && 
                        resource.getData().getUsername() != null && 
                        !resource.getData().getUsername().isEmpty()) {
                        
                        String fetchedUsername = resource.getData().getUsername();
                        // Update UI with real name
                        binding.tvPlayerName.setText(fetchedUsername);
                        
                        // Update player in our list
                        player.setUsername(fetchedUsername);

                        // Update host indicator if this player is the host
                        View chipHostInObserver = itemView.findViewById(R.id.chip_host);
                        if (chipHostInObserver != null) {
                            boolean updatedIsHost = hostUsername != null &&
                                                    fetchedUsername.equals(hostUsername);
                            chipHostInObserver.setVisibility(updatedIsHost ? View.VISIBLE : View.GONE);
                        }
                    }
                });
            }
            
            // Make sure the view is fully visible with proper background and text colors
            itemView.setBackgroundResource(R.color.white);
            
            // Set player name text color (text is set earlier or by observer)
            binding.tvPlayerName.setTextColor(0xFF000000);
            binding.tvPlayerName.setVisibility(View.VISIBLE);
            
            // Determine if this player is the host
            boolean isHost = hostUsername != null && 
                    player.getUsername() != null && 
                    player.getUsername().equals(hostUsername);
            
            // Use hardcoded IDs to get views directly from the itemView since binding may have issues
            View tvHostIndicator = itemView.findViewById(R.id.tvHostIndicator);
            View chipHost = itemView.findViewById(R.id.chip_host);
            ImageView playerAvatar = itemView.findViewById(R.id.imgPlayerAvatar);
            ImageView statusIndicator = itemView.findViewById(R.id.statusIndicator);
            
            if (tvHostIndicator != null) {
                tvHostIndicator.setVisibility(View.GONE);
            }
            
            if (chipHost != null) {
                chipHost.setVisibility(isHost ? View.VISIBLE : View.GONE);
            }
            
            // Set avatar
            if (playerAvatar != null) {
                playerAvatar.setImageResource(R.drawable.ic_default_avatar);
                playerAvatar.setVisibility(View.VISIBLE);
            }
            
            // Set status indicator
            if (statusIndicator != null) {
                try {
                    if (player.isReady()) {
                        statusIndicator.setImageResource(R.drawable.ic_status_ready);
                    } else {
                        statusIndicator.setImageResource(R.drawable.ic_status_waiting);
                    }
                    statusIndicator.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    statusIndicator.setVisibility(View.GONE);
                }
            }
        }
    }
}
