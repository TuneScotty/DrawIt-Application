package com.example.drawit.views;

import android.util.Log;
import com.example.drawit.game.FirebaseHandler;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.models.Player;
import com.google.firebase.database.DataSnapshot;

public class GameInitializer {
    private static final String TAG = "GameInitializer";
    private final FirebaseHandler firebaseHandler;
    private final String lobbyId;
    private final String userId;
    private final GameManager gameManager;
    private final boolean isHost;

    public GameInitializer(FirebaseHandler firebaseHandler, String lobbyId, String userId, GameManager gameManager, boolean isHost) {
        this.firebaseHandler = firebaseHandler;
        this.lobbyId = lobbyId;
        this.userId = userId;
        this.gameManager = gameManager;
        this.isHost = isHost;
        
        // Validate parameters to prevent issues later
        if (firebaseHandler == null) {
            Log.e(TAG, "Firebase handler cannot be null");
        }
        
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.e(TAG, "Lobby ID cannot be null or empty");
        }
        
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "User ID cannot be null or empty");
        }
        
        if (gameManager == null) {
            Log.e(TAG, "Game manager cannot be null");
        }
    }

    /**
     * Initialize the game with the current player
     */
    public void initializeGame() {
        try {
            // Create a player object for the current user
            String displayName = "Player";
            
            // Try to get display name from Firebase Auth if available
            if (firebaseHandler != null && firebaseHandler.getCurrentUser() != null) {
                displayName = firebaseHandler.getCurrentUser().getDisplayName();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = "Player"; // Fallback name if display name is null or empty
                }
            }
            
            // Initialize the game with the current player
            Player player = gameManager.addPlayer(userId, displayName);
            if (player != null) {
                Log.d(TAG, "Added player to game: " + player.getId() + " (" + player.getName() + ")");
                
                // Set host status if this is the host
                if (isHost) {
                    // gameManager.setHost(userId); // Method not found in GameManager
                    // Set host status using Player object instead
                    Player currentPlayer = gameManager.getGame().findPlayerById(userId);
                    if (currentPlayer != null) {
                        currentPlayer.setHost(true);
                    }
                    Log.d(TAG, "Set player as host: " + userId);
                }
                
                // Initialize players from lobby to ensure all players are loaded
                initializePlayersFromLobby();
            } else {
                Log.e(TAG, "Failed to add player to game: " + userId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing game", e);
            throw new RuntimeException("Failed to initialize game", e);
        }
    }

    /**
     * Initialize players from the lobby in a safer way
     * This method fetches players from Firebase but doesn't throw exceptions that would
     * terminate the game initialization process
     */
    private void initializePlayersFromLobby() {
        try {
            if (firebaseHandler == null) {
                Log.e(TAG, "Firebase handler is null, skipping player initialization");
                return;
            }
            
            if (lobbyId == null || lobbyId.isEmpty()) {
                Log.e(TAG, "Lobby ID is null or empty, skipping player initialization");
                return;
            }

            // For safety, wrap in try-catch to prevent initialization failures
            Log.d(TAG, "Initializing players from lobby: " + lobbyId);
            firebaseHandler.getLobbiesRef().child(lobbyId).child("players").get().addOnSuccessListener(snapshot -> {
                try {
                    if (snapshot.exists()) {
                        for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                            String playerId = playerSnapshot.getKey();
                            if (playerId != null && !playerId.equals(userId)) { // Skip current user as they're already added
                                // Get player name if available
                                String playerName = "Player";
                                if (playerSnapshot.hasChild("username")) {
                                    playerName = playerSnapshot.child("username").getValue(String.class);
                                }

                                // Add player to game
                                if (playerName != null) {
                                    gameManager.addPlayer(playerId, playerName);
                                    Log.d(TAG, "Added player from lobby: " + playerId + " (" + playerName + ")");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Catch any exceptions during player processing, but don't crash the game
                    Log.e(TAG, "Error processing players from lobby", e);
                }
            }).addOnFailureListener(e -> {
                // Just log the error but don't propagate it
                Log.e(TAG, "Error getting players from lobby", e);
            });
        } catch (Exception e) {
            // Catch any exceptions during the Firebase request setup
            Log.e(TAG, "Error initializing players from lobby", e);
        }
    }

    /**
     * Start the game if all conditions are met
     * @return true if the game was started, false otherwise
     */
    public boolean startGameIfReady() {
        if (!isHost) {
            Log.w(TAG, "Only the host can start the game");
            return false;
        }
        
        try {
            // Check if we have enough players
            // int playerCount = gameManager.getPlayerCount(); // Method not found in GameManager
            int playerCount = gameManager.getGame().getPlayers().size(); // Get player count directly from game
            if (playerCount < 1) { // Minimum 1 player for testing
                Log.w(TAG, "Not enough players to start the game (current: " + playerCount + ")");
                return false;
            }
            
            Log.d(TAG, "Starting game with " + playerCount + " players");
            
            // Start the game using the game manager
            boolean started = gameManager.startGame();
            if (!started) {
                Log.e(TAG, "Failed to start game - game manager returned false");
                return false;
            }
            
            Log.d(TAG, "Game started successfully");
            
            // Notify Firebase that the game has started
            if (firebaseHandler != null) {
                firebaseHandler.startGame(lobbyId, task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        Log.e(TAG, "Failed to start game in Firebase", task.getException());
                    } else {
                        Log.d(TAG, "Game started in Firebase");
                    }
                });
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting game", e);
            return false;
        }
    }
}
