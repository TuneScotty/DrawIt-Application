package com.example.drawit.game;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ChildEventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

import com.example.drawit.models.Lobby;
import com.example.drawit.models.Player;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.DrawingAction;

public class FirebaseHandler {
    private static FirebaseHandler instance;
    private final FirebaseAuth auth;
    private final DatabaseReference lobbiesRef;
    private final DatabaseReference inGameLobbiesRef;
    private static final String TAG = "FirebaseHandler";
    private final Map<String, ValueEventListener> activeLobbyListeners;

    private FirebaseHandler() {
        auth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        lobbiesRef = database.getReference("lobbies");
        inGameLobbiesRef = database.getReference("ingame_lobbies");
        activeLobbyListeners = new HashMap<>();
        
        Log.d(TAG, "Database URL: " + database.getReference().toString());
        Log.d(TAG, "Auth Status: " + (auth.getCurrentUser() != null ? "Authenticated" : "Not Authenticated"));
        if (auth.getCurrentUser() != null) {
            Log.d(TAG, "User ID: " + auth.getCurrentUser().getUid());
        }
    }

    public static synchronized FirebaseHandler getInstance() {
        if (instance == null) {
            instance = new FirebaseHandler();
        }
        return instance;
    }
    
    /**
     * Get a reference to a game in the database
     * @param gameId The ID of the game
     * @return DatabaseReference to the game
     */
    public DatabaseReference getGameReference(String gameId) {
        return inGameLobbiesRef.child(gameId);
    }

    public DatabaseReference getLobbiesRef() {
        return lobbiesRef;
    }
    
    /**
     * Get drawing actions for a specific round and player for rating
     * 
     * @param lobbyId The ID of the lobby
     * @param roundNumber The round number
     * @param playerId The ID of the player who drew
     * @param callback Callback with the list of drawing actions
     */
    public void getDrawingForRound(String lobbyId, int roundNumber, String playerId, DrawingCallback callback) {
        if (lobbyId == null || lobbyId.isEmpty() || playerId == null || playerId.isEmpty()) {
            Log.e(TAG, "Invalid parameters for getDrawingForRound");
            callback.onDrawingLoaded(new ArrayList<>());
            return;
        }
        
        Log.d(TAG, "Fetching drawings for lobby: " + lobbyId + ", round: " + roundNumber + ", player: " + playerId);
        
        // Construct multiple possible paths to the drawings for the specified round and player
        // Try both formats to ensure we find the drawings
        String drawingsPath1 = String.format("rounds/%d/playerDrawings/%s", roundNumber, playerId);
        String drawingsPath2 = String.format("drawings/%s/%d/%s", lobbyId, roundNumber, playerId);
        String drawingsPath3 = String.format("drawings/round%d/player%s", roundNumber, playerId);
        
        Log.d(TAG, "Trying multiple paths to find drawings:");
        Log.d(TAG, "Path 1: " + drawingsPath1);
        Log.d(TAG, "Path 2: " + drawingsPath2);
        Log.d(TAG, "Path 3: " + drawingsPath3);
        
        // First try path 1
        inGameLobbiesRef.child(lobbyId).child(drawingsPath1).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                List<DrawingAction> drawingActions = new ArrayList<>();
                for (DataSnapshot actionSnapshot : task.getResult().getChildren()) {
                    DrawingAction action = actionSnapshot.getValue(DrawingAction.class);
                    if (action != null) {
                        drawingActions.add(action);
                    }
                }
                Log.d(TAG, "Path 1 successful! Loaded " + drawingActions.size() + " drawing actions");
                callback.onDrawingLoaded(drawingActions);
            } else {
                Log.d(TAG, "Path 1 failed, trying path 2...");
                
                // Try path 2 (direct reference)
                DatabaseReference path2Ref = FirebaseDatabase.getInstance().getReference(drawingsPath2);
                path2Ref.get().addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful() && task2.getResult() != null && task2.getResult().exists()) {
                        List<DrawingAction> drawingActions = new ArrayList<>();
                        for (DataSnapshot actionSnapshot : task2.getResult().getChildren()) {
                            DrawingAction action = actionSnapshot.getValue(DrawingAction.class);
                            if (action != null) {
                                drawingActions.add(action);
                            }
                        }
                        Log.d(TAG, "Path 2 successful! Loaded " + drawingActions.size() + " drawing actions");
                        callback.onDrawingLoaded(drawingActions);
                    } else {
                        Log.d(TAG, "Path 2 failed, trying path 3...");
                        
                        // Try path 3 (with round/player prefixes)
                        DatabaseReference path3Ref = FirebaseDatabase.getInstance().getReference(drawingsPath3);
                        path3Ref.get().addOnCompleteListener(task3 -> {
                            if (task3.isSuccessful() && task3.getResult() != null && task3.getResult().exists()) {
                                List<DrawingAction> drawingActions = new ArrayList<>();
                                for (DataSnapshot actionSnapshot : task3.getResult().getChildren()) {
                                    DrawingAction action = actionSnapshot.getValue(DrawingAction.class);
                                    if (action != null) {
                                        drawingActions.add(action);
                                    }
                                }
                                Log.d(TAG, "Path 3 successful! Loaded " + drawingActions.size() + " drawing actions");
                                callback.onDrawingLoaded(drawingActions);
                            } else {
                                Log.e(TAG, "All paths failed. No drawings found for player " + playerId + " in round " + roundNumber);
                                callback.onDrawingLoaded(new ArrayList<>());
                            }
                        });
                    }
                });
            }
        });
    }
    
    /**
     * Save drawing actions to Firebase for a specific round and player
     * This ensures drawings are stored in all possible paths for reliable retrieval
     * 
     * @param lobbyId The lobby ID
     * @param roundNumber The round number
     * @param playerId The player ID who created the drawing
     * @param drawingActions The list of drawing actions to save
     */
    public void saveDrawingForRound(String lobbyId, int roundNumber, String playerId, List<DrawingAction> drawingActions) {
        if (lobbyId == null || lobbyId.isEmpty() || playerId == null || playerId.isEmpty() || drawingActions == null) {
            Log.e(TAG, "Invalid parameters for saveDrawingForRound");
            return;
        }
        
        Log.d(TAG, "Saving " + drawingActions.size() + " drawing actions for player " + playerId + " in round " + roundNumber);
        
        // Save to all three paths to ensure retrieval works properly
        String path1 = String.format("rounds/%d/playerDrawings/%s", roundNumber, playerId);
        String path2 = String.format("drawings/%s/%d/%s", lobbyId, roundNumber, playerId);
        String path3 = String.format("drawings/round%d/player%s", roundNumber, playerId);
        
        // Create a map with drawing actions indexed by their ID or position
        Map<String, Object> drawingsMap = new HashMap<>();
        for (int i = 0; i < drawingActions.size(); i++) {
            DrawingAction action = drawingActions.get(i);
            // Use action ID if available, otherwise use the index
            String key = (action.getActionId() != null) ? action.getActionId() : "action_" + i;
            drawingsMap.put(key, action);
        }
        
        // Save to path 1
        inGameLobbiesRef.child(lobbyId).child(path1).setValue(drawingsMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully saved drawings to path 1"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save drawings to path 1: " + e.getMessage()));
        
        // Save to path 2
        DatabaseReference path2Ref = FirebaseDatabase.getInstance().getReference(path2);
        path2Ref.setValue(drawingsMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully saved drawings to path 2"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save drawings to path 2: " + e.getMessage()));
        
        // Save to path 3
        DatabaseReference path3Ref = FirebaseDatabase.getInstance().getReference(path3);
        path3Ref.setValue(drawingsMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully saved drawings to path 3"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save drawings to path 3: " + e.getMessage()));
    }
    
    /**
     * Update game status in Firebase
     * 
     * @param lobbyId Lobby ID
     * @param status New game status
     */
    public void updateGameStatus(String lobbyId, GameStatus status) {
        if (lobbyId == null || status == null) {
            Log.e(TAG, "Invalid parameters for updateGameStatus");
            return;
        }
        
        String path = "lobbies/" + lobbyId + "/status";
        DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference(path);
        statusRef.setValue(status.toString())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Game status updated to " + status))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update game status: " + e.getMessage()));
    }
    
    /**
     * Callback interface for loading drawing actions
     */
    public interface DrawingCallback {
        void onDrawingLoaded(List<DrawingAction> drawings);
    }
    
    public DatabaseReference getInGameLobbiesRef() {
        return inGameLobbiesRef;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void createLobby(String lobbyName, String password, OnCompleteListener<String> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No authenticated user found.");
            return;
        }

        String lobbyId = lobbiesRef.push().getKey();
        if (lobbyId == null) {
            Log.e(TAG, "Failed to generate lobby ID.");
            return;
        }

        Map<String, Object> lobbyData = new HashMap<>();
        lobbyData.put("name", lobbyName);
        lobbyData.put("password", password);
        lobbyData.put("playersCount", 1);
        lobbyData.put("creator", user.getUid());
        lobbyData.put("createdAt", ServerValue.TIMESTAMP);
        lobbyData.put("isActive", true); // Add isActive flag
        
        Map<String, Object> players = new HashMap<>();
        Map<String, Object> playerJoinData = new HashMap<>();
        playerJoinData.put("uid", user.getUid()); // Optional: store UID if not just the key
        playerJoinData.put("joinedAt", ServerValue.TIMESTAMP);
        players.put(user.getUid(), playerJoinData);
        lobbyData.put("players", players);

        Log.d(TAG, "Creating lobby with ID: " + lobbyId);
        Log.d(TAG, "User ID: " + user.getUid());

        lobbiesRef.child(lobbyId)
            .setValue(lobbyData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Lobby " + lobbyId + " created successfully by " + user.getUid());
                setupHostDisconnectHandlers(lobbyId, user.getUid()); // Setup onDisconnect for the initial host
                if (callback != null) {
                    callback.onComplete(Tasks.forResult(lobbyId));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to create lobby", e);
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public void setupHostDisconnectHandlers(String lobbyId, String hostId) {
        if (lobbyId == null || hostId == null) {
            Log.e(TAG, "Lobby ID or Host ID is null, cannot set up disconnect handlers.");
            return;
        }
        Log.d(TAG, "Setting up onDisconnect handlers for host: " + hostId + " on lobby: " + lobbyId);

        DatabaseReference lobbyRef = lobbiesRef.child(lobbyId);
        DatabaseReference playerRef = lobbyRef.child("players").child(hostId);
        DatabaseReference playerCountRef = lobbyRef.child("playersCount");
        DatabaseReference isActiveRef = lobbyRef.child("isActive");

        // Clear any previous onDisconnect operations to avoid conflicts
        playerRef.onDisconnect().cancel();
        playerCountRef.onDisconnect().cancel();
        isActiveRef.onDisconnect().cancel();
        
        // If the host disconnects, remove their player entry
        playerRef.onDisconnect().removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) Log.d(TAG, "onDisconnect: playerRef removeValue set for " + hostId);
            else Log.e(TAG, "onDisconnect: failed to set playerRef removeValue for " + hostId, task.getException());
        });

        // If the host disconnects, decrement player count
        playerCountRef.onDisconnect().setValue(ServerValue.increment(-1)).addOnCompleteListener(task -> {
            if (task.isSuccessful()) Log.d(TAG, "onDisconnect: playerCountRef decrement set for " + lobbyId);
            else Log.e(TAG, "onDisconnect: failed to set playerCountRef decrement for " + lobbyId, task.getException());
        });
        
        // Check if this user is the creator/host
        lobbyRef.child("creator").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String currentCreator = task.getResult().getValue(String.class);
                if (hostId.equals(currentCreator)) { 
                    // If the host disconnects, mark the lobby as inactive instead of removing it
                    // This makes it easier to debug and prevents data loss
                    isActiveRef.onDisconnect().setValue(false).addOnCompleteListener(opTask -> {
                        if (opTask.isSuccessful()) {
                            Log.d(TAG, "onDisconnect: Lobby " + lobbyId + " will be marked inactive when host " + hostId + " disconnects");
                        } else {
                            Log.e(TAG, "onDisconnect: Failed to set inactive status for lobby " + lobbyId, opTask.getException());
                        }
                    });
                }
            }
        });
    }

    public void joinLobby(String lobbyId, String enteredPassword, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        lobbiesRef.child(lobbyId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onComplete(Tasks.forException(new Exception("Lobby not found")));
                return;
            }

            String password = snapshot.child("password").getValue(String.class);
            if (password != null && !password.isEmpty() && !password.equals(enteredPassword)) {
                callback.onComplete(Tasks.forException(new Exception("Incorrect password")));
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            Map<String, Object> playerJoinData = new HashMap<>();
            playerJoinData.put("uid", user.getUid()); // Optional: store UID if not just the key
            playerJoinData.put("joinedAt", ServerValue.TIMESTAMP);
            updates.put("players/" + user.getUid(), playerJoinData); // Store player object with joinedAt
            updates.put("playersCount", ServerValue.increment(1));

            lobbiesRef.child(lobbyId).updateChildren(updates)
                .addOnCompleteListener(callback);
        });
    }

    /**
     * Manually clean up inactive lobbies from the database.
     * This can be called periodically to remove lobbies that were not properly cleaned up.
     */
    public void cleanupInactiveLobbies() {
        Log.d(TAG, "Starting cleanup of inactive lobbies");
        
        lobbiesRef.orderByChild("isActive").equalTo(false).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int count = 0;
                for (DataSnapshot lobbySnapshot : task.getResult().getChildren()) {
                    String lobbyId = lobbySnapshot.getKey();
                    if (lobbyId != null) {
                        count++;
                        Log.d(TAG, "Removing inactive lobby: " + lobbyId);
                        lobbiesRef.child(lobbyId).removeValue();
                    }
                }
                Log.d(TAG, "Cleaned up " + count + " inactive lobbies");
            } else {
                Log.e(TAG, "Failed to query inactive lobbies", task.getException());
            }
        });
    }
    
    public void leaveLobby(String lobbyId, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            Log.w(TAG, "User not logged in, cannot leave lobby.");
            if (callback != null) callback.onComplete(Tasks.forException(new Exception("User not logged in")));
            return;
        }
        final String currentUserId = user.getUid();

        lobbiesRef.child(lobbyId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Log.e(TAG, "Lobby not found or error fetching: " + lobbyId, task.getException());
                if (callback != null) callback.onComplete(Tasks.forException(new Exception("Lobby not found")));
                return;
            }
            DataSnapshot snapshot = task.getResult();
            String hostId = snapshot.child("creator").getValue(String.class);
            Long playersCountLong = snapshot.child("playersCount").getValue(Long.class);
            int playersCount = (playersCountLong != null) ? playersCountLong.intValue() : 0;

            if (playersCount <= 1 || currentUserId.equals(hostId)) {
                // If only one player left, or the host is leaving, remove the lobby
                Log.d(TAG, "Removing lobby " + lobbyId + " as it will be empty or host left.");
                lobbiesRef.child(lobbyId).removeValue().addOnCompleteListener(callback);
            } else {
                Map<String, Object> updates = new HashMap<>();
                updates.put("players/" + currentUserId, null); // Remove user from players
                updates.put("playersCount", ServerValue.increment(-1));

                // Host transfer logic (if current host is leaving and it's not the last player)
                // This part is now redundant if the above (playersCount <=1 || currentUserId.equals(hostId)) handles host leaving by removing lobby.
                // For clarity, let's refine host transfer if host leaves AND there are other players.
                // The current logic: if host leaves, lobby is removed. If we want host transfer, this needs adjustment.
                // Let's assume for now the primary host leaving with others present means host transfer.
                // For this, the condition `playersCount <= 1 || currentUserId.equals(hostId)` needs to be split.
                // If `currentUserId.equals(hostId)` AND `playersCount > 1`, THEN transfer host.
                // ELSE if `playersCount <=1`, THEN remove lobby.

                // Revised logic for host leaving:
                if (currentUserId.equals(hostId)) { // Host is leaving
                    if (playersCount > 1) { // And there are other players
                        // Find new host (earliest joined player who is not the current host)
                        String newHostId = null;
                        long earliestJoinTime = Long.MAX_VALUE;
                        DataSnapshot playersSnapshot = snapshot.child("players");
                        for (DataSnapshot playerEntry : playersSnapshot.getChildren()) {
                            if (playerEntry.getKey().equals(currentUserId)) continue; // Skip the leaving host

                            Long joinedAt = playerEntry.child("joinedAt").getValue(Long.class);
                            if (joinedAt != null && joinedAt < earliestJoinTime) {
                                earliestJoinTime = joinedAt;
                                newHostId = playerEntry.getKey();
                            }
                        }
                        if (newHostId != null) {
                            updates.put("creator", newHostId);
                            Log.d(TAG, "Host transferred to " + newHostId + " in lobby " + lobbyId);
                            setupHostDisconnectHandlers(lobbyId, newHostId); // Setup onDisconnect for the new host
                        } else {
                            // Should not happen if playersCount > 1 and host is leaving
                            // Means no other valid player found, just remove lobby
                            Log.w(TAG, "Host leaving, but no new host found. Removing lobby " + lobbyId);
                            lobbiesRef.child(lobbyId).removeValue().addOnCompleteListener(callback);
                            return; // Exit early
                        }
                    } else { // Host is leaving and is the last player
                        Log.d(TAG, "Host is last player. Removing lobby " + lobbyId);
                        lobbiesRef.child(lobbyId).removeValue().addOnCompleteListener(callback);
                        return; // Exit early
                    }
                }
                // If not host leaving, or if host transfer happened, apply updates.
                Log.d(TAG, "Updating lobby " + lobbyId + " after player left.");
                lobbiesRef.child(lobbyId).updateChildren(updates).addOnCompleteListener(callback);
            }
        });
    }

    // Custom ValueEventListener class to avoid anonymous inner class issues during dexing
    private static class SafeLobbyValueEventListener implements com.google.firebase.database.ValueEventListener {
        private final String lobbyId;
        private final LobbyListener listener;
        private final String TAG;
        
        public SafeLobbyValueEventListener(String lobbyId, LobbyListener listener, String tag) {
            this.lobbyId = lobbyId;
            this.listener = listener;
            this.TAG = tag;
        }
        
        @Override
        public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
            if (listener == null) {
                Log.w(TAG, "LobbyListener is null in onDataChange for lobbyId: " + lobbyId);
                return;
            }

            if (!snapshot.exists()) {
                Log.d(TAG, "Lobby data does not exist for lobbyId: " + lobbyId + ". Calling onLobbyDeleted.");
                listener.onLobbyDeleted();
                return;
            }

            try {
                Lobby lobby = snapshot.getValue(Lobby.class); // Firebase attempts to map to your Lobby model
                if (lobby != null) {
                    // It's crucial that Lobby.java has a default constructor and public getters/setters
                    // for Firebase to deserialize correctly.
                    // Also, ensure fields in Lobby.java match the keys in Firebase.
                    lobby.setId(snapshot.getKey()); // Manually set the ID if not part of the stored data
                    listener.onLobbyUpdated(lobby);

                    // Check for host changes explicitly if needed, though LobbyActivity also does this
                    String hostId = snapshot.child("creator").getValue(String.class);
                    if (hostId != null) {
                        // This call is still fine if LobbyActivity needs it, but LobbyActivity already checks host status
                        // based on the Lobby object in onLobbyUpdated.
                        // Consider if this specific call is redundant or if LobbyActivity should solely rely on onLobbyUpdated.
                        // For now, keeping it if FirebaseHandler is meant to emit this specific event.
                        // However, ensure LobbyListener *has* this method. The current one does.
                        listener.onHostChanged(hostId);
                    }

                } else {
                    Log.e(TAG, "Failed to parse lobby data for lobbyId: " + lobbyId);
                    listener.onError("Failed to parse lobby data.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing lobby data snapshot for lobbyId: " + lobbyId, e);
                listener.onError("Error processing lobby data: " + e.getMessage());
            }
        }

        @Override
        public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
            if (listener != null) {
                Log.e(TAG, "Lobby data listener cancelled for lobbyId: " + lobbyId, error.toException());
                listener.onError(error.getMessage());
            }
        }
    }

    public void addLobbyListener(String lobbyId, LobbyListener listener) {
        try {
            if (lobbyId == null || listener == null || lobbiesRef == null) {
                Log.e(TAG, "Invalid parameters in addLobbyListener");
                return;
            }
            
            // Remove any existing listener for this lobby ID to prevent duplicates
            try {
                removeLobbyListener(lobbyId, listener);
            } catch (Exception e) {
                Log.e(TAG, "Error removing existing listener", e);
            }
            
            // Create a named class instance instead of anonymous inner class
            SafeLobbyValueEventListener valueEventListener = new SafeLobbyValueEventListener(lobbyId, listener, TAG);
            
            // Add the listener to Firebase
            ValueEventListener registeredListener = lobbiesRef.child(lobbyId).addValueEventListener(valueEventListener);
            
            // Store the listener for later removal
            activeLobbyListeners.put(lobbyId + "_" + listener.hashCode(), registeredListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add lobby listener", e);
        }
    }

    public void removeLobbyListener(String lobbyId, LobbyListener listener) {
        DatabaseReference lobbyRef = lobbiesRef.child(lobbyId);
        ValueEventListener currentListener = activeLobbyListeners.get(lobbyId + "_" + listener.hashCode());
        if (currentListener != null) {
            lobbyRef.removeEventListener(currentListener);
            activeLobbyListeners.remove(lobbyId + "_" + listener.hashCode());
            Log.d(TAG, "Removed lobby listener for lobby: " + lobbyId);
        } else {
            Log.d(TAG, "No active listener found to remove for lobby: " + lobbyId + " and listener hash: " + listener.hashCode());
        }
    }

    public void loginUser(String email, String password, OnCompleteListener<Void> callback) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                Log.d(TAG, "Login Successful: " + email);
                if (callback != null) {
                    callback.onComplete(Tasks.forResult(null));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Login Failed: " + e.getMessage());
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public void registerUser(String email, String password, OnCompleteListener<Void> callback) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                Log.d(TAG, "Registration Successful: " + email);
                if (callback != null) {
                    callback.onComplete(Tasks.forResult(null));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Registration Failed: " + e.getMessage());
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public void logoutUser() {
        auth.signOut();
    }

    public void resetPassword(String email, OnCompleteListener<Void> callback) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Password Reset Email Sent: " + email);
                if (callback != null) {
                    callback.onComplete(Tasks.forResult(null));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Password Reset Failed: " + e.getMessage());
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public void deleteAccount(OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            user.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Account Deleted Successfully");
                    if (callback != null) {
                        callback.onComplete(Tasks.forResult(null));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Account Deletion Failed: " + e.getMessage());
                    if (callback != null) {
                        callback.onComplete(Tasks.forException(e));
                    }
                });
        }
    }

    public void updateEmail(String newEmail, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            user.updateEmail(newEmail)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Email Updated Successfully: " + newEmail);
                    if (callback != null) {
                        callback.onComplete(Tasks.forResult(null));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Email Update Failed: " + e.getMessage());
                    if (callback != null) {
                        callback.onComplete(Tasks.forException(e));
                    }
                });
        }
    }

    public void updatePassword(String newPassword, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            user.updatePassword(newPassword)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Password Updated Successfully");
                    if (callback != null) {
                        callback.onComplete(Tasks.forResult(null));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Password Update Failed: " + e.getMessage());
                    if (callback != null) {
                        callback.onComplete(Tasks.forException(e));
                    }
                });
        }
    }

    public void startGame(String lobbyId, OnCompleteListener<Void> callback) {
        Log.d(TAG, "Attempting to start game for lobby: " + lobbyId);
        
        // First, mark the lobby as having a game started
        Map<String, Object> lobbyUpdates = new HashMap<>();
        lobbyUpdates.put("gameStarted", true);
        lobbyUpdates.put("gameStartedAt", ServerValue.TIMESTAMP);
        
        // Then, initialize the game state in the in-game database
        // This ensures all clients have the same initial game state
        Map<String, Object> gameInitData = new HashMap<>();
        gameInitData.put("status", GameStatus.STARTED.toString());
        gameInitData.put("currentRound", 1);
        gameInitData.put("currentDrawerIndex", 0);
        gameInitData.put("createdAt", ServerValue.TIMESTAMP);
        
        Log.d(TAG, "Setting gameStarted=true for lobby: " + lobbyId);
        
        // Update the lobby first
        lobbiesRef.child(lobbyId).updateChildren(lobbyUpdates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Lobby marked as started, now initializing game state");
                
                // Then initialize the game state
                inGameLobbiesRef.child(lobbyId).updateChildren(gameInitData)
                    .addOnSuccessListener(gameVoid -> {
                        Log.d(TAG, "Game state initialized successfully for lobby: " + lobbyId);
                        if (callback != null) {
                            callback.onComplete(Tasks.forResult(null));
                        }
                    })
                    .addOnFailureListener(gameError -> {
                        Log.e(TAG, "Failed to initialize game state for lobby: " + lobbyId, gameError);
                        if (callback != null) {
                            callback.onComplete(Tasks.forException(gameError));
                        }
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to mark lobby as started: " + lobbyId, e);
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public void deleteLobby(String lobbyId) {
        Log.d(TAG, "Deleting lobby: " + lobbyId);
        lobbiesRef.child(lobbyId).removeValue()
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Lobby " + lobbyId + " deleted successfully."))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to delete lobby " + lobbyId, e));
    }

    public void addInGameLobbyListener(String lobbyId, GameStartedListener listener) {
        Log.d(TAG, "addInGameLobbyListener called for lobby: " + lobbyId + ". This is a placeholder.");
        // Example: inGameLobbiesRef.child(lobbyId).addValueEventListener(new ValueEventListener() { ... });
        // For now, we'll simulate an immediate callback for testing if needed, or leave it non-functional.
        // if (listener != null) listener.onGameStarted(false); // Simulate game not started or handle appropriately
    }

    public interface GameStartedListener {
        void onGameStarted(boolean gameStarted);
    }

    public void updateLobbyState(String lobbyId, String state, OnCompleteListener<Void> callback) {
        if (getCurrentUser() == null) {
            if (callback != null) {
                callback.onComplete(Tasks.forException(new Exception("User not logged in")));
            }
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("state", state);
        updates.put("isActive", !"GAME_STARTED".equals(state)); // Hide lobby when game starts

        lobbiesRef.child(lobbyId)
            .updateChildren(updates)
            .addOnCompleteListener(callback);
    }

    public void updateGameState(String lobbyId, Map<String, Object> updates) {
        if (getCurrentUser() == null) return;
        inGameLobbiesRef.child(lobbyId).updateChildren(updates);
    }
    
    /**
     * Synchronize the full game object with Firebase
     */
    public void syncGameToFirebase(String lobbyId, com.example.drawit.game.models.Game game) {
        if (getCurrentUser() == null || game == null) return;
        
        Log.d(TAG, "Syncing game data to Firebase for lobby: " + lobbyId);
        inGameLobbiesRef.child(lobbyId).child("gameData").setValue(game)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Game data synced successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to sync game data", e));
    }
    
    /**
     * Add a drawing action to the current round
     */
    public void addDrawingAction(String lobbyId, com.example.drawit.game.models.DrawingAction action) {
        if (getCurrentUser() == null || action == null) return;
        
        inGameLobbiesRef.child(lobbyId).child("drawingActions")
            .push().setValue(action)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to add drawing action", e));
    }
    
    /**
     * Add a drawing action to a specific player's drawing list
     * @param lobbyId The lobby ID
     * @param playerId The player ID
     * @param action The drawing action
     */
    public void addPlayerDrawingAction(String lobbyId, String playerId, com.example.drawit.game.models.DrawingAction action) {
        if (getCurrentUser() == null || lobbyId == null || playerId == null || action == null) return;
        
        inGameLobbiesRef.child(lobbyId).child("playerDrawings").child(playerId).push().setValue(action);
    }
    
    /**
     * Update a player's rating for another player's drawing
     * @param lobbyId The lobby ID
     * @param drawingPlayerId The player ID whose drawing is being rated
     * @param raterPlayerId The player ID who is rating the drawing
     * @param rating The rating (1-3)
     */
    public void updateGameRating(String lobbyId, String drawingPlayerId, String raterPlayerId, int rating) {
        if (getCurrentUser() == null || lobbyId == null || drawingPlayerId == null || raterPlayerId == null) return;
        
        inGameLobbiesRef.child(lobbyId).child("ratings").child(drawingPlayerId).child(raterPlayerId).setValue(rating);
    }
    
    /**
     * Update the word choices for the current round
     * @param lobbyId The lobby ID
     * @param wordChoices The list of word choices
     */
    public void updateGameWordChoices(String lobbyId, List<String> wordChoices) {
        if (getCurrentUser() == null || lobbyId == null || wordChoices == null) return;
        
        inGameLobbiesRef.child(lobbyId).child("wordChoices").setValue(wordChoices);
    }
    
    /**
     * Send a drawing action to other players in real-time
     * @param lobbyId The ID of the lobby
     * @param action The drawing action to send
     */
    public void sendDrawingAction(String lobbyId, com.example.drawit.game.models.DrawingAction action) {
        if (getCurrentUser() == null || action == null || lobbyId == null) {
            Log.e(TAG, "Cannot send drawing action: invalid parameters");
            return;
        }
        
        // Set the drawer ID if not already set
        if (action.getDrawerId() == null) {
            action.setDrawerId(getCurrentUser().getUid());
        }
        
        // Set timestamp if not already set
        if (action.getTimestamp() == 0) {
            action.setTimestamp(System.currentTimeMillis());
        }
        
        Log.d(TAG, "Sending drawing action: " + action.getActionType() + " for lobby: " + lobbyId);
        
        // Store in Firebase
        inGameLobbiesRef.child(lobbyId).child("drawingActions")
            .push().setValue(action)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Drawing action sent successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to send drawing action", e));
    }
    
    /**
     * Submit a guess for the current round
     */
    public void submitGuess(String lobbyId, com.example.drawit.game.models.Guess guess) {
        if (getCurrentUser() == null || guess == null) return;
        
        inGameLobbiesRef.child(lobbyId).child("guesses")
            .push().setValue(guess)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to submit guess", e));
    }
    
    /**
     * Listen for game data changes
     * @return The registered ValueEventListener for later removal
     */
    public ValueEventListener listenForGameUpdates(String lobbyId, ValueEventListener listener) {
        if (getCurrentUser() == null) return null;
        
        inGameLobbiesRef.child(lobbyId).child("gameData")
            .addValueEventListener(listener);
        
        return listener;
    }
    
    /**
     * Listen for new drawing actions
     * @return The registered ChildEventListener for later removal
     */
    public ChildEventListener listenForDrawingActions(String lobbyId, ChildEventListener listener) {
        if (getCurrentUser() == null) return null;
        
        inGameLobbiesRef.child(lobbyId).child("drawingActions")
            .addChildEventListener(listener);
        
        return listener;
    }
    
    /**
     * Listen for new guesses
     * @return The registered ChildEventListener for later removal
     */
    public ChildEventListener listenForGuesses(String lobbyId, ChildEventListener listener) {
        if (getCurrentUser() == null) return null;
        
        inGameLobbiesRef.child(lobbyId).child("guesses")
            .addChildEventListener(listener);
        
        return listener;
    }

    public void updateLobbyPassword(String lobbyId, String newPassword, OnCompleteListener<Void> callback) {
        if (lobbyId == null) {
            Log.e(TAG, "Lobby ID is null, cannot update password.");
            if (callback != null) callback.onComplete(Tasks.forException(new Exception("Lobby ID is null")));
            return;
        }
        // An empty string for newPassword means the lobby becomes unlocked.
        // Storing null might also work but empty string is explicit for "no password".
        String passwordToStore = (newPassword == null || newPassword.trim().isEmpty()) ? "" : newPassword.trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put("password", passwordToStore);

        lobbiesRef.child(lobbyId).updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Lobby " + lobbyId + " password updated successfully.");
                if (callback != null) {
                    callback.onComplete(Tasks.forResult(null));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update password for lobby " + lobbyId, e);
                if (callback != null) {
                    callback.onComplete(Tasks.forException(e));
                }
            });
    }

    public interface LobbyCreationListener {
        void onLobbyCreated(String lobbyId);
        void onLobbyCreationFailed(Exception e);
    }

    public interface LobbyJoinListener {
        void onLobbyJoined(String lobbyId);
        void onLobbyJoinFailed(String message);
    }

    public interface LobbyLeaveListener {
        void onLobbyLeft();
        void onLobbyLeaveFailed(String message);
    }

    public interface GameStateListener {
        void onGameStarted();
        void onGameStartFailed(String message);
    }

    public interface LobbyPasswordUpdateListener {
        void onPasswordUpdated();
        void onPasswordUpdateFailed(Exception e);
    }

    public interface LobbyListener {
        void onLobbyUpdated(Lobby lobby);
        void onPlayerAdded(Player player);
        void onPlayerRemoved(String playerId);
        void onHostChanged(String newHostId);
        void onLobbyDeleted();
        void onError(String message);
    }
    
    /**
     * Handles the termination of a game session for a specific lobby.
     * Updates the game state to ENDED and removes all listeners.
     * 
     * @param lobbyId The unique identifier of the lobby for which the game is ending.
     */
    public void endGame(String lobbyId) {
        if (lobbyId == null) {
            Log.e(TAG, "Cannot end game: lobbyId is null");
            return;
        }
        
        Log.d(TAG, "Game ended for lobby: " + lobbyId);
        
        // Remove all listeners for this lobby
        removeGameListeners(lobbyId);
        
        // Update game state to ENDED
        inGameLobbiesRef.child(lobbyId).child("gameState").setValue("ENDED")
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Game state updated to ENDED for lobby: " + lobbyId))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update game state for lobby: " + lobbyId, e));
    }
    
    /**
     * Removes all Firebase listeners associated with a specific game/lobby.
     * This helps prevent memory leaks and channel errors when the activity is destroyed.
     * 
     * @param lobbyId The unique identifier of the lobby to remove listeners for.
     */
    public void removeGameListeners(String lobbyId) {
        if (lobbyId == null) {
            Log.e(TAG, "Cannot remove game listeners: lobbyId is null");
            return;
        }
        
        try {
            Log.d(TAG, "Removing all game listeners for lobby: " + lobbyId);
            
            // Remove any active lobby listeners
            if (activeLobbyListeners.containsKey(lobbyId)) {
                ValueEventListener listener = activeLobbyListeners.get(lobbyId);
                if (listener != null) {
                    lobbiesRef.child(lobbyId).removeEventListener(listener);
                }
                activeLobbyListeners.remove(lobbyId);
            }
            
            // Remove game data listeners
            inGameLobbiesRef.child(lobbyId).child("gameData").removeEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {}
                
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            
            // Remove drawing actions listeners
            inGameLobbiesRef.child(lobbyId).child("drawingActions").removeEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
                
                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            
            // Remove guesses listeners
            inGameLobbiesRef.child(lobbyId).child("guesses").removeEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
                
                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
                
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            
            Log.d(TAG, "Successfully removed all game listeners for lobby: " + lobbyId);
        } catch (Exception e) {
            Log.e(TAG, "Error removing game listeners for lobby: " + lobbyId, e);
        }
    }
}
