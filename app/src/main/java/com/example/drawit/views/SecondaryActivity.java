package com.example.drawit.views;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.drawit.adapters.LobbyAdapter;
import com.example.drawit.models.Lobby;
import com.example.drawit.views.MainActivity;
import com.example.drawit.views.LobbyActivity;
import com.example.drawit.R;
import com.example.drawit.game.FirebaseHandler;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class SecondaryActivity extends AppCompatActivity {
    private FirebaseHandler firebaseHandler;
    private RecyclerView lobbyRecyclerView;
    private LobbyAdapter lobbyAdapter;
    private List<Lobby> lobbies;
    private ValueEventListener lobbyListener;
    private Button createLobbyButton;
    private static final String TAG = "SecondaryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        firebaseHandler = FirebaseHandler.getInstance();
        
        if (firebaseHandler.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        firebaseHandler.cleanupInactiveLobbies();

        lobbies = new ArrayList<>();
        setupRecyclerView();
        setupCreateLobbyButton();
        attachLobbiesListener();
    }

    private void setupRecyclerView() {
        lobbyRecyclerView = findViewById(R.id.lobbiesRecyclerView);
        lobbyAdapter = new LobbyAdapter(lobbies, new LobbyAdapter.OnLobbyClickListener() {
            @Override
            public void onLobbyClick(Lobby lobby) {
                // Handle lobby click if needed
            }

            @Override
            public void onJoinClick(Lobby lobby) {
                if (lobby == null || lobby.getId() == null) {
                    Toast.makeText(SecondaryActivity.this, "Invalid lobby data.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (lobby.hasPassword()) {
                    // Lobby is password protected, prompt for password
                    AlertDialog.Builder builder = new AlertDialog.Builder(SecondaryActivity.this);
                    builder.setTitle("Enter Password");

                    final View customLayout = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
                    builder.setView(customLayout);
                    final EditText passwordInput = customLayout.findViewById(R.id.passwordInput);

                    builder.setPositiveButton("Join", (dialog, which) -> {
                        String enteredPassword = passwordInput.getText().toString();
                        joinLobbyWithPassword(lobby, enteredPassword);
                    });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    builder.show();
                } else {
                    // Lobby is not password protected
                    joinLobbyWithPassword(lobby, ""); // Pass empty string for non-protected lobbies
                }
            }
        });
        lobbyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        lobbyRecyclerView.setAdapter(lobbyAdapter);
    }

    private void joinLobbyWithPassword(Lobby lobby, String enteredPassword) {
        if (lobby == null || lobby.getId() == null) {
            Log.e(TAG, "joinLobbyWithPassword: Lobby or Lobby ID is null.");
            Toast.makeText(SecondaryActivity.this, "Error joining lobby: Invalid lobby data.", Toast.LENGTH_SHORT).show();
            return;
        }
        firebaseHandler.joinLobby(lobby.getId(), enteredPassword, task -> {
            runOnUiThread(() -> {
                if (!task.isSuccessful()) {
                    String errorMessage = (task.getException() != null && task.getException().getMessage() != null) ?
                            task.getException().getMessage() : "Failed to join lobby. Unknown error.";
                    Log.e(TAG, "Failed to join lobby " + lobby.getId() + ": " + errorMessage, task.getException());
                    Toast.makeText(SecondaryActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Successfully joined or attempted to join (if password was correct or not needed)
                try {
                    Intent intent = new Intent(SecondaryActivity.this, LobbyActivity.class);
                    intent.putExtra("LOBBY_ID", lobby.getId());
                    intent.putExtra("IS_HOST", false); // User joining is not the host
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting LobbyActivity", e);
                    Toast.makeText(SecondaryActivity.this, "Error starting lobby: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupCreateLobbyButton() {
        createLobbyButton = findViewById(R.id.createLobbyButton);
        createLobbyButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_lobby, null);
            EditText lobbyNameInput = dialogView.findViewById(R.id.lobbyNameInput);
            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

            builder.setView(dialogView)
                .setTitle("Create New Lobby")
                .setPositiveButton("Create", (dialog, which) -> {
                    String lobbyName = lobbyNameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString().trim();
                    
                    if (!lobbyName.isEmpty()) {
                        firebaseHandler.createLobby(lobbyName, password, task -> {
                            runOnUiThread(() -> {
                                if (!task.isSuccessful()) {
                                    String errorMessage = task.getException() != null ? 
                                        task.getException().getMessage() : "Failed to create lobby";
                                    Toast.makeText(SecondaryActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                String lobbyId = task.getResult();
                                try {
                                    Intent intent = new Intent(SecondaryActivity.this, LobbyActivity.class);
                                    intent.putExtra("LOBBY_ID", lobbyId);
                                    intent.putExtra("IS_HOST", true);
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error starting LobbyActivity", e);
                                    Toast.makeText(SecondaryActivity.this, "Error starting lobby: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    } else {
                        Toast.makeText(SecondaryActivity.this, "Please enter a lobby name", 
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void attachLobbiesListener() {
        // Detach any existing listener first to prevent duplicates
        detachListeners();
        
        // Make sure Firebase is initialized
        if (firebaseHandler == null || firebaseHandler.getLobbiesRef() == null) {
            Log.e(TAG, "Cannot attach lobby listener: Firebase not initialized");
            return;
        }
        
        lobbyListener = firebaseHandler.getLobbiesRef().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                lobbies.clear();
                for (DataSnapshot lobbySnapshot : snapshot.getChildren()) {
                    String lobbyId = lobbySnapshot.getKey();
                    String name = lobbySnapshot.child("name").getValue(String.class);
                    String creator = lobbySnapshot.child("creator").getValue(String.class);
                    Long playersCountLong = lobbySnapshot.child("playersCount").getValue(Long.class);
                    // Ensure password field is checked safely
                    String passwordHash = lobbySnapshot.child("password").getValue(String.class);
                    // Default isActive to true if the field doesn't exist in older records
                    Boolean isActive = lobbySnapshot.child("isActive").exists() ? 
                        lobbySnapshot.child("isActive").getValue(Boolean.class) : true;

                    // Default playersCount to 0 if null
                    int playersCount = (playersCountLong != null) ? playersCountLong.intValue() : 0;

                    // Only add lobbies that have the required fields and are active
                    if (lobbyId != null && name != null && creator != null && (isActive == null || isActive)) {
                        Lobby lobby = new Lobby(lobbyId, name, creator, playersCount, passwordHash);
                        // Explicitly set the active state from Firebase
                        lobby.setActive(isActive == null || isActive);
                        lobbies.add(lobby);
                        Log.d(TAG, "Added lobby to list: " + lobbyId + ", name: " + name + ", active: " + lobby.isActive());
                    } else {
                        Log.d(TAG, "Skipped lobby: " + lobbyId + ", active status: " + isActive);
                    }
                }
                // Update the adapter with the new list
                if (lobbyAdapter != null) {
                    lobbyAdapter.updateLobbies(lobbies);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read lobbies", error.toException());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Properly detach all Firebase listeners
        detachListeners();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Detach listeners when the activity is not in the foreground
        detachListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reattach listeners when the activity comes back to the foreground
        if (firebaseHandler != null && firebaseHandler.getCurrentUser() != null) {
            attachLobbiesListener();
        }
    }
    
    private void detachListeners() {
        // Remove all Firebase listeners to prevent memory leaks
        if (lobbyListener != null && firebaseHandler != null) {
            try {
                firebaseHandler.getLobbiesRef().removeEventListener(lobbyListener);
                lobbyListener = null;
            } catch (Exception e) {
                Log.e(TAG, "Error detaching lobby listener", e);
            }
        }
    }
}
