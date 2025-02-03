package com.example.myt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myt.adapters.PlayersAdapter;
import com.google.android.gms.tasks.Task;
import java.util.ArrayList;
import java.util.List;

public class LobbyActivity extends AppCompatActivity implements FirebaseHandler.LobbyListener {
    private static final String TAG = "LobbyActivity";
    private FirebaseHandler firebaseHandler;
    private String lobbyId;
    private boolean isHost;
    private List<String> players;
    private PlayersAdapter playersAdapter;
    private Button startButton;
    private Button lockButton;
    private TextView lobbyNameText;
    private RecyclerView playersRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        lobbyId = getIntent().getStringExtra("LOBBY_ID");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        
        if (lobbyId == null) {
            Log.e(TAG, "No lobby ID provided");
            finish();
            return;
        }

        firebaseHandler = FirebaseHandler.getInstance();
        players = new ArrayList<>();
        
        setupViews();
        setupRecyclerView();
        setupGameListener();
        firebaseHandler.addLobbyListener(lobbyId, this);
    }

    private void setupViews() {
        startButton = findViewById(R.id.startIntermissionButton);
        lockButton = findViewById(R.id.toggleLockButton);
        lobbyNameText = findViewById(R.id.lobbyNameText);
        playersRecyclerView = findViewById(R.id.playersRecyclerView);

        firebaseHandler.getLobbiesRef().child(lobbyId).child("name").get()
                .addOnSuccessListener(dataSnapshot -> lobbyNameText.setText(dataSnapshot.getValue(String.class)));

        startButton.setEnabled(isHost);
        lockButton.setEnabled(isHost);

        startButton.setOnClickListener(v -> startIntermission());
        lockButton.setOnClickListener(v -> toggleLobbyLock());

        Button leaveButton = findViewById(R.id.leaveButton);
        leaveButton.setOnClickListener(v -> leaveLobby());
    }

    private void setupRecyclerView() {
        playersAdapter = new PlayersAdapter(players);
        playersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        playersRecyclerView.setAdapter(playersAdapter);
    }

    private void leaveLobby() {
        firebaseHandler.leaveLobby(lobbyId, task -> {
            runOnUiThread(() -> {
                if (!task.isSuccessful() && task.getException() != null) {
                    Toast.makeText(this, "Failed to leave lobby: " + 
                        task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                finish();
            });
        });
    }

    private void startIntermission() {
        if (!isHost) {
            Toast.makeText(this, "Only the host can start the game", Toast.LENGTH_SHORT).show();
            return;
        }

        if (players.size() < 2) {
            Toast.makeText(this, "Need at least 2 players to start", Toast.LENGTH_SHORT).show();
            return;
        }

        firebaseHandler.startGame(lobbyId, task -> {
            if (task.isSuccessful()) {
                Intent gameIntent = new Intent(this, GameActivity.class);
                gameIntent.putExtra("LOBBY_ID", lobbyId);
                startActivity(gameIntent);
            } else {
                Toast.makeText(this, "Failed to start game", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleLobbyLock() {
        if (!isHost) {
            Toast.makeText(this, "Only the host can lock/unlock the lobby", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Implement lobby lock toggle
        Toast.makeText(this, "Toggling lobby lock...", Toast.LENGTH_SHORT).show();
    }

    private void setupGameListener() {
        firebaseHandler.addInGameLobbyListener(lobbyId, gameStarted -> {
            if (gameStarted) {
                Intent gameIntent = new Intent(this, GameActivity.class);
                gameIntent.putExtra("LOBBY_ID", lobbyId);
                startActivity(gameIntent);
                finish();
            }
        });
    }

    @Override
    public void onPlayersChanged(List<String> updatedPlayers) {
        runOnUiThread(() -> {
            players.clear();
            players.addAll(updatedPlayers);
            playersAdapter.notifyDataSetChanged();
            
            startButton.setEnabled(isHost && players.size() >= 2);
        });
    }

    @Override
    public void onLobbyDeleted() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Lobby was closed by the host", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onHostChanged(String newHostId) {
        runOnUiThread(() -> {
            isHost = newHostId.equals(firebaseHandler.getCurrentUser().getUid());
            startButton.setEnabled(isHost && players.size() >= 2);
            lockButton.setEnabled(isHost);
            
            if (isHost) {
                Toast.makeText(this, "You are now the host", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLobbyStateChanged(String state) {
        runOnUiThread(() -> {
            if ("GAME_STARTED".equals(state)) {
                Intent gameIntent = new Intent(this, GameActivity.class);
                gameIntent.putExtra("LOBBY_ID", lobbyId);
                gameIntent.putExtra("HOST_ID", firebaseHandler.getCurrentUser().getUid());
                startActivity(gameIntent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        firebaseHandler.removeLobbyListener(lobbyId);
        if (isHost) {
            firebaseHandler.deleteLobby(lobbyId);
        }
    }
} 