package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.ItemPlayerBinding;
import com.example.drawit_app.model.User;

import java.util.List;

/**
 * Adapter for displaying a list of players in a lobby
 */
public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    private List<User> players;
    private String hostUsername;

    public PlayerAdapter(List<User> players) {
        this.players = players;
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

    public void updatePlayers(List<User> newPlayers) {
        this.players = newPlayers;
        notifyDataSetChanged();
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
            // Set player name with fallback to prevent null display
            String username = player.getUsername();
            if (username == null || username.isEmpty()) {
                username = "Player";
            }
            
            // Make sure the view is fully visible with proper background and text colors
            itemView.setBackgroundResource(R.color.white);
            
            // Set player name
            binding.tvPlayerName.setText(username);
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
