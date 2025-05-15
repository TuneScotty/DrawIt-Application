package com.example.drawit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.view.View;
import android.app.ProgressDialog;

import com.example.drawit.models.Lobby;
import com.example.drawit.models.Player; // Ensure this model exists and is correctly defined
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LobbyActivity extends AppCompatActivity implements FirebaseHandler.LobbyListener {

    private static final String TAG = "LobbyActivity";

    private RecyclerView playerRecyclerView;
    private TextView lobbyNameTextView;
    private PlayerAdapter playerAdapter;
    private List<Player> playerList = new ArrayList<>();
    private ImageButton lockLobbyButton;
    private Button gameStartButton; // Renamed from startGameButton to avoid ID conflicts

    private FirebaseHandler firebaseHandler;
    private String lobbyId;
    private boolean isHost;
    private Lobby currentLobbyData;

    /**
     * Initializes the activity, sets up the user interface, and retrieves lobby data.
     * This method is called when the activity is first created.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     *                           Otherwise, it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        firebaseHandler = FirebaseHandler.getInstance(); // Changed this line
        lobbyId = getIntent().getStringExtra("LOBBY_ID");
        isHost = getIntent().getBooleanExtra("IS_HOST", false); // Initial host status

        lobbyNameTextView = findViewById(R.id.lobbyNameTextView);
        playerRecyclerView = findViewById(R.id.playerRecyclerView);
        lockLobbyButton = findViewById(R.id.lockLobbyButton); // Ensure this ID matches your XML
        gameStartButton = findViewById(R.id.gameStartButton); // Updated to match the new ID in XML
        Button leaveButton = findViewById(R.id.leaveButton); // Get reference to leave button

        setupPlayerRecyclerView();
        setupLobbyLockInteraction();
        setupStartGameButton();
        setupLeaveButton(leaveButton);

        if (lobbyId != null) {
            firebaseHandler.addLobbyListener(lobbyId, this);
        } else {
            Log.e(TAG, "Lobby ID is null. Cannot display lobby.");
            Toast.makeText(this, "Error: Lobby ID missing.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Sets up the RecyclerView for displaying the list of players in the lobby.
     * Initializes the adapter and layout manager.
     */
    private void setupPlayerRecyclerView() {
        String currentUserId = "";
        FirebaseUser user = firebaseHandler.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }
        playerAdapter = new PlayerAdapter(playerList, currentUserId);
        playerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        playerRecyclerView.setAdapter(playerAdapter);
    }

    /**
     * Configures the interaction for the lobby lock button.
     * Sets an OnClickListener to toggle the lobby lock status if the current user is the host.
     */
    private void setupLobbyLockInteraction() {
        lockLobbyButton.setOnClickListener(v -> {
            if (!isHost) {
                Toast.makeText(LobbyActivity.this, "Only the host can change lobby settings.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentLobbyData == null) {
                Toast.makeText(LobbyActivity.this, "Lobby data not available. Please wait.", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleLobbyLock();
        });
    }

    /**
     * Configures the start game button.
     * Sets an OnClickListener to initiate the game start process if the current user is the host.
     */
    /**
     * Sets up the leave button functionality.
     * When clicked, the current user will leave the lobby and return to the main screen.
     * 
     * @param leaveButton The Button view to set up with the leave functionality
     */
    private void setupLeaveButton(Button leaveButton) {
        leaveButton.setOnClickListener(v -> {
            if (lobbyId == null) {
                Toast.makeText(this, "Error: No lobby to leave", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // Show a loading indicator
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Leaving lobby...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Call the leaveLobby method in FirebaseHandler
            firebaseHandler.leaveLobby(lobbyId, task -> {
                progressDialog.dismiss();
                
                if (task.isSuccessful()) {
                    Log.d(TAG, "Successfully left lobby: " + lobbyId);
                    Toast.makeText(this, "Left lobby successfully", Toast.LENGTH_SHORT).show();
                    
                    // Return to the main screen
                    Intent intent = new Intent(this, MainActivity2.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clear the back stack
                    startActivity(intent);
                    finish();
                } else {
                    String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Log.e(TAG, "Failed to leave lobby: " + errorMsg);
                    Toast.makeText(this, "Failed to leave lobby: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    
    private void setupStartGameButton() {
        gameStartButton.setOnClickListener(v -> {
            if (!isHost) {
                Toast.makeText(LobbyActivity.this, "Only the host can start the game.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (lobbyId == null || currentLobbyData == null) {
                 Toast.makeText(LobbyActivity.this, "Lobby data not loaded yet.", Toast.LENGTH_SHORT).show();
                 return;
            }
            // Require at least 2 players to start a game
            int minPlayersToStart = 2; // Game requires at least 2 players
            if (playerList.size() < minPlayersToStart) {
                 Toast.makeText(LobbyActivity.this, "At least 2 players are required to start the game.", Toast.LENGTH_SHORT).show();
                 return;
            }
            firebaseHandler.startGame(lobbyId, task -> {
                if (task.isSuccessful()) {
                    Intent intent = new Intent(LobbyActivity.this, GameActivity.class); // Ensure GameActivity exists
                    intent.putExtra("LOBBY_ID", lobbyId);
                    intent.putExtra("IS_HOST", isHost);
                    startActivity(intent);
                    finish(); 
                } else {
                    String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(LobbyActivity.this, "Failed to start game: " + errorMsg, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to start game", task.getException());
                }
            });
        });
    }

    /**
     * Toggles the lock status of the lobby.
     * If the lobby is currently locked, it presents options to change the password or unlock the lobby.
     * If the lobby is unlocked, it prompts the host to set a new password.
     * This action can only be performed by the host.
     */
    private void toggleLobbyLock() {
        if (currentLobbyData == null) {
             Toast.makeText(this, "Lobby data not loaded.", Toast.LENGTH_SHORT).show();
             return;
        }

        if (currentLobbyData.hasPassword()) {
            new AlertDialog.Builder(this)
                .setTitle("Lobby Locked")
                .setMessage("Manage lobby password:")
                .setPositiveButton("Change Password", (dialog, which) -> promptForNewPassword(true))
                .setNegativeButton("Unlock Lobby", (dialog, which) -> firebaseHandler.updateLobbyPassword(lobbyId, "", task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LobbyActivity.this, "Lobby unlocked successfully.", Toast.LENGTH_SHORT).show();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Failed to unlock";
                        Toast.makeText(LobbyActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNeutralButton("Cancel", null)
                .show();
        } else {
            promptForNewPassword(false);
        }
    }

    /**
     * Prompts the host to enter a password for the lobby, either to set a new one or change an existing one.
     *
     * @param isChangingPassword True if an existing password is being changed, false if a new password is being set for an unlocked lobby.
     */
    private void promptForNewPassword(boolean isChangingPassword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isChangingPassword ? "Change Lobby Password" : "Set Lobby Password");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_enter_password, null);
        final EditText passwordInput = viewInflated.findViewById(R.id.passwordInput);
        passwordInput.setHint(isChangingPassword ? "Enter new password" : "Enter password");
        builder.setView(viewInflated);

        builder.setPositiveButton(isChangingPassword ? "Change" : "Set", (dialog, which) -> {
            String newPassword = passwordInput.getText().toString().trim();
            
            if (newPassword.isEmpty() && !isChangingPassword) {
                Toast.makeText(LobbyActivity.this, "Password cannot be empty to lock the lobby.", Toast.LENGTH_SHORT).show();
                return; 
            }
            
            firebaseHandler.updateLobbyPassword(lobbyId, newPassword, task -> {
                if (task.isSuccessful()) {
                    String message = newPassword.isEmpty() && isChangingPassword ? "Lobby unlocked successfully." : "Lobby password updated successfully.";
                    if (newPassword.isEmpty() && !isChangingPassword) {
                         message = "Lobby remains unlocked."; // Or handle as no-op if they try to set empty on unlocked.
                    }
                    Toast.makeText(LobbyActivity.this, message, Toast.LENGTH_SHORT).show();
                } else {
                    String error = task.getException() != null ? task.getException().getMessage() : "Failed to update password";
                    Toast.makeText(LobbyActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Updates the enabled state and visibility of host-specific UI controls.
     * These controls include buttons for starting the game and managing the lobby lock.
     * Their state is determined by whether the current user is the host and other game-specific conditions (e.g., player count).
     */
    private void updateHostControls() {
        int minPlayersToStart = 2; // Game requires at least 2 players
        
        boolean canStartGame = isHost && playerList.size() >= minPlayersToStart;
        gameStartButton.setEnabled(canStartGame);
        if (isHost && playerList.size() < minPlayersToStart) {
            gameStartButton.setAlpha(0.5f); // Visual indication that the button is disabled
        } else {
            gameStartButton.setAlpha(1.0f);
        }
        gameStartButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
        
        lockLobbyButton.setEnabled(isHost);
        lockLobbyButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the visual representation of the lobby lock button, typically its icon,
     * to reflect whether the lobby is currently password-protected.
     *
     * @param isCurrentlyLocked True if the lobby is password-protected, false otherwise.
     */
    private void updateLockButtonVisuals(boolean isCurrentlyLocked) {
        if (isCurrentlyLocked) {
            lockLobbyButton.setImageResource(R.drawable.ic_lock); // Ensure R.drawable.ic_lock exists
        } else {
            lockLobbyButton.setImageResource(R.drawable.ic_lock_open); // Ensure R.drawable.ic_lock_open exists
        }
    }

    /**
     * Called when the lobby data is updated in Firebase.
     * This method is responsible for refreshing the activity's UI with the latest lobby information,
     * including the lobby name, player list, and host-specific controls.
     *
     * @param lobby The updated {@link Lobby} object containing the latest data. If null, it implies the lobby may have been deleted or is inaccessible.
     */
    @Override
    public void onLobbyUpdated(Lobby lobby) {
        runOnUiThread(() -> {
            if (lobby == null) {
                onLobbyDeleted(); 
                return;
            }
            currentLobbyData = lobby;
            
            // Check if game has started - this will affect ALL players, not just the host
            if (lobby.getGameStarted() != null && lobby.getGameStarted()) {
                Log.d(TAG, "Game has started, redirecting to GameActivity");
                Intent intent = new Intent(LobbyActivity.this, GameActivity.class);
                intent.putExtra("LOBBY_ID", lobbyId);
                intent.putExtra("IS_HOST", isHost);
                startActivity(intent);
                finish();
                return;
            }
            
            lobbyNameTextView.setText(lobby.getName());

            FirebaseUser currentUser = firebaseHandler.getCurrentUser();
            if (currentUser != null) {
                isHost = currentUser.getUid().equals(lobby.getCreator());
            } else {
                isHost = false;
                Log.e(TAG, "Current user is null in onLobbyUpdated, cannot determine host status.");
                Toast.makeText(this, "Authentication error. Please restart.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            updatePlayerListFromMap(lobby.getPlayers());
            updateHostControls();
            updateLockButtonVisuals(lobby.hasPassword());
        });
    }
    
    /**
     * Updates the local player list based on the data retrieved from Firebase.
     * The player data is expected to be a map where keys are player UIDs.
     *
     * @param playersMap A map of player UIDs to their associated data. The structure of the value
     *                   depends on how player data is stored in Firebase (e.g., could be another map
     *                   containing player details like 'username' or 'joinedAt').
     */
    private void updatePlayerListFromMap(Map<String, Object> playersMap) {
        playerList.clear();
        if (playersMap != null) {
            for (Map.Entry<String, Object> entry : playersMap.entrySet()) {
                String playerId = entry.getKey();
                Object playerDataValue = entry.getValue();
                if (playerDataValue instanceof Map) {
                    Map<String, Object> playerData = (Map<String, Object>) playerDataValue;
                    String username = (String) playerData.get("username"); // Assuming 'username' field exists
                    if (username == null) username = "Player"; // Default username if not found
                    
                    // Ensure your Player model can be constructed this way or adapt as necessary
                    Player player = new Player(playerId); 
                    // If Player model stores username: player.setUsername(username); 
                    playerList.add(player);
                } else {
                     // Fallback if player data isn't a map, just use ID
                    playerList.add(new Player(playerId));
                }
            }
        }
        playerAdapter.notifyDataSetChanged();
    }

    /**
     * Callback triggered when a new player is detected as having been added to the lobby in Firebase.
     * This implementation relies on {@link #onLobbyUpdated(Lobby)} for comprehensive updates,
     * so this method primarily serves as a log point or for more granular UI updates if desired.
     *
     * @param player The {@link Player} object representing the added player.
     */
    @Override
    public void onPlayerAdded(Player player) {
        Log.d(TAG, "Player added event: " + player.getId() + ". Full update handled by onLobbyUpdated.");
    }

    /**
     * Callback triggered when a player is detected as having been removed from the lobby in Firebase.
     * Similar to {@link #onPlayerAdded(Player)}, this relies on {@link #onLobbyUpdated(Lobby)} for list refreshing.
     *
     * @param playerId The UID of the player who was removed.
     */
    @Override
    public void onPlayerRemoved(String playerId) {
        Log.d(TAG, "Player removed event: " + playerId + ". Full update handled by onLobbyUpdated.");
    }

    /**
     * Callback triggered when the host of the lobby changes in Firebase.
     * Updates the local {@code isHost} flag and refreshes host-specific UI controls.
     *
     * @param newHostId The UID of the new host.
     */
    @Override
    public void onHostChanged(String newHostId) {
       runOnUiThread(() -> {
            FirebaseUser currentUser = firebaseHandler.getCurrentUser();
            if (currentUser != null) {
                isHost = currentUser.getUid().equals(newHostId);
                updateHostControls();
                if (isHost) {
                    Toast.makeText(this, "You are now the host!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Current user null in onHostChanged. Cannot update host status.");
            }
        });
    }

    /**
     * Callback triggered when the lobby is detected as having been deleted from Firebase.
     * Notifies the user and closes the {@code LobbyActivity}.
     */
    @Override
    public void onLobbyDeleted() {
        runOnUiThread(() -> {
            Toast.makeText(LobbyActivity.this, "This lobby has been closed.", Toast.LENGTH_LONG).show();
            currentLobbyData = null; 
            finish();
        });
    }

    /**
     * Callback triggered when an error occurs within the Firebase lobby listener.
     * Logs the error, displays a message to the user, and typically closes the activity.
     *
     * @param message A descriptive message of the error that occurred.
     */
    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            Log.e(TAG, "Lobby listener error: " + message);
            Toast.makeText(LobbyActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    /**
     * Cleans up resources when the activity is being destroyed.
     * Primarily, this involves removing the Firebase lobby listener to prevent memory leaks
     * and unnecessary background operations.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firebaseHandler != null && lobbyId != null) {
            firebaseHandler.removeLobbyListener(lobbyId, this);
        }
    }
}