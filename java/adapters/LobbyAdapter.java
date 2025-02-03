package com.example.myt.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myt.R;
import com.example.myt.models.Lobby;

import java.util.List;

public class LobbyAdapter extends RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder> {
    private List<Lobby> lobbies;
    private OnLobbyClickListener listener;
    private Context context;

    public interface OnLobbyClickListener {
        void onLobbyClick(Lobby lobby);
        void onJoinClick(Lobby lobby);
    }

    public LobbyAdapter(List<Lobby> lobbies, OnLobbyClickListener listener) {
        this.lobbies = lobbies;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LobbyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
            .inflate(R.layout.item_lobby, parent, false);
        return new LobbyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LobbyViewHolder holder, int position) {
        Lobby lobby = lobbies.get(position);
        holder.lobbyName.setText(lobby.getName());
        holder.playerCount.setText(String.format("Players: %d", lobby.getPlayersCount()));
        holder.lockIcon.setVisibility(lobby.hasPassword() ? View.VISIBLE : View.GONE);
        
        holder.joinButton.setOnClickListener(v -> {
            if (lobby.hasPassword()) {
                showPasswordDialog(lobby);
            } else {
                if (listener != null) {
                    listener.onJoinClick(lobby);
                }
            }
        });
    }

    private void showPasswordDialog(Lobby lobby) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_lobby_password, null);
        TextView passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setView(dialogView)
            .setTitle("Enter Lobby Password")
            .setPositiveButton("Join", (dialog, which) -> {
                String password = passwordInput.getText().toString().trim();
                if (!password.isEmpty()) {
                    lobby.setEnteredPassword(password);
                    if (listener != null) {
                        listener.onJoinClick(lobby);
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public int getItemCount() {
        return lobbies.size();
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
} 