package com.example.myt;

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
import com.example.myt.adapters.LobbyAdapter;
import com.example.myt.models.Lobby;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class MainActivity2 extends AppCompatActivity {
    private FirebaseHandler firebaseHandler;
    private RecyclerView lobbyRecyclerView;
    private LobbyAdapter lobbyAdapter;
    private List<Lobby> lobbies;
    private ValueEventListener lobbyListener;
    private Button createLobbyButton;
    private static final String TAG = "MainActivity2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        firebaseHandler = FirebaseHandler.getInstance();
        
        if (firebaseHandler.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

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
                firebaseHandler.joinLobby(lobby.getId(), lobby.getEnteredPassword(), task -> {
                    runOnUiThread(() -> {
                        if (!task.isSuccessful()) {
                            String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to join lobby";
                            Toast.makeText(MainActivity2.this, errorMessage, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent intent = new Intent(MainActivity2.this, LobbyActivity.class);
                        intent.putExtra("LOBBY_ID", lobby.getId());
                        intent.putExtra("IS_HOST", false);
                        startActivity(intent);
                    });
                });
            }
        });
        lobbyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        lobbyRecyclerView.setAdapter(lobbyAdapter);
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
                                    Toast.makeText(MainActivity2.this, errorMessage, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                String lobbyId = task.getResult();
                                Intent intent = new Intent(MainActivity2.this, LobbyActivity.class);
                                intent.putExtra("LOBBY_ID", lobbyId);
                                intent.putExtra("IS_HOST", true);
                                startActivity(intent);
                            });
                        });
                    } else {
                        Toast.makeText(MainActivity2.this, "Please enter a lobby name", 
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void attachLobbiesListener() {
        lobbyListener = firebaseHandler.getLobbiesRef().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                lobbies.clear();
                for (DataSnapshot lobbySnapshot : snapshot.getChildren()) {
                    String lobbyId = lobbySnapshot.getKey();
                    String name = lobbySnapshot.child("name").getValue(String.class);
                    String creator = lobbySnapshot.child("creator").getValue(String.class);
                    Long playersCount = lobbySnapshot.child("playersCount").getValue(Long.class);
                    Boolean hasPassword = lobbySnapshot.child("password").getValue(String.class) != null;

                    if (lobbyId != null && name != null && creator != null && playersCount != null) {
                        Lobby lobby = new Lobby(lobbyId, name, creator, playersCount.intValue(), hasPassword);
                        lobbies.add(lobby);
                    }
                }
                lobbyAdapter.notifyDataSetChanged();
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
        if (lobbyListener != null) {
            firebaseHandler.getLobbiesRef().removeEventListener(lobbyListener);
        }
    }
}
