package com.example.myt.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.myt.R;

import java.util.List;

public class LobbyAdapter extends RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder> {

    private final List<String> lobbyNames; // List of lobby names (or other data)

    // Constructor that takes a list of lobby names (strings)
    public LobbyAdapter(List<String> lobbyNames) {
        this.lobbyNames = lobbyNames;
    }

    @Override
    public LobbyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate the custom item layout (recycler_item.xml)
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_item, parent, false);
        return new LobbyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LobbyViewHolder holder, int position) {
        // Get the current lobby name from the list
        String lobbyName = lobbyNames.get(position);

        // Set the lobby name to the TextView inside the ViewHolder
        holder.lobbyNameText.setText(lobbyName);
    }

    @Override
    public int getItemCount() {
        return lobbyNames.size(); // Return the size of the list (number of items)
    }

    // ViewHolder to hold the view components for each item
    public static class LobbyViewHolder extends RecyclerView.ViewHolder {
        TextView lobbyNameText;

        public LobbyViewHolder(View itemView) {
            super(itemView);
            // Initialize the TextView from the inflated layout
            lobbyNameText = itemView.findViewById(R.id.lobbyNameText);
        }
    }
}
