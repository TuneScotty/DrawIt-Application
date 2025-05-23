package com.example.drawit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.drawit.models.Player;
import java.util.List;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    private List<Player> playerList;
    private String currentUserId;

    public PlayerAdapter(List<Player> playerList, String currentUserId) {
        this.playerList = playerList;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false); // Using a simple built-in layout
        return new PlayerViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        Player player = playerList.get(position);
        String displayText = player.getUsername() != null ? player.getUsername() : player.getId();
        if (player.getId().equals(currentUserId)) {
            displayText += " (You)";
        }
        // Potentially, you can also indicate the host here if you pass that information
        // For example, if (player.getId().equals(hostId)) { displayText += " (Host)"; }
        holder.playerNameTextView.setText(displayText);
    }

    @Override
    public int getItemCount() {
        return playerList.size();
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        TextView playerNameTextView;

        PlayerViewHolder(View view) {
            super(view);
            // For android.R.layout.simple_list_item_1, the TextView has ID @android:id/text1
            playerNameTextView = view.findViewById(android.R.id.text1);
        }
    }
}
