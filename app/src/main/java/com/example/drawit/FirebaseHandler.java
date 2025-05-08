package com.example.drawit;

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
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

public class FirebaseHandler {
    private static FirebaseHandler instance;
    private final FirebaseAuth auth;
    private final DatabaseReference lobbiesRef;
    private final DatabaseReference inGameLobbiesRef;
    private static final String TAG = "FirebaseHandler";
    private final Map<String, ValueEventListener> lobbyListeners;

    private FirebaseHandler() {
        auth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        lobbiesRef = database.getReference("lobbies");
        inGameLobbiesRef = database.getReference("ingame_lobbies");
        lobbyListeners = new HashMap<>();
        
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

    public DatabaseReference getLobbiesRef() {
        return lobbiesRef;
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
        
        Map<String, Object> players = new HashMap<>();
        players.put(user.getUid(), true);
        lobbyData.put("players", players);

        Log.d(TAG, "Creating lobby with ID: " + lobbyId);
        Log.d(TAG, "User ID: " + user.getUid());

        lobbiesRef.child(lobbyId)
            .setValue(lobbyData)
            .addOnSuccessListener(aVoid -> {
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
            updates.put("players/" + user.getUid(), true);
            updates.put("playersCount", ServerValue.increment(1));

            lobbiesRef.child(lobbyId).updateChildren(updates)
                .addOnCompleteListener(callback);
        });
    }

    public void leaveLobby(String lobbyId, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        lobbiesRef.child(lobbyId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onComplete(Tasks.forResult(null));
                return;
            }

            String hostId = snapshot.child("creator").getValue(String.class);
            long playersCount = snapshot.child("playersCount").getValue(Long.class);

            if (playersCount <= 1 || (hostId != null && hostId.equals(user.getUid()))) {
                lobbiesRef.child(lobbyId).removeValue().addOnCompleteListener(callback);
            } else {
                Map<String, Object> updates = new HashMap<>();
                updates.put("players/" + user.getUid(), null);
                updates.put("playersCount", ServerValue.increment(-1));
                
                if (hostId != null && hostId.equals(user.getUid())) {
                    snapshot.child("players").getChildren().forEach(playerSnapshot -> {
                        if (!playerSnapshot.getKey().equals(user.getUid())) {
                            updates.put("creator", playerSnapshot.getKey());
                            return;
                        }
                    });
                }

                lobbiesRef.child(lobbyId).updateChildren(updates).addOnCompleteListener(callback);
            }
        });
    }

    // LobbyListener interface is defined below

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
            try {
                if (snapshot == null || listener == null) {
                    Log.e(TAG, "Null snapshot or listener in SafeLobbyValueEventListener");
                    return;
                }
                
                // Check if lobby is active
                boolean isDeleted = !snapshot.exists();
                try {
                    if (!isDeleted && snapshot.child("isActive").exists()) {
                        Boolean isActive = snapshot.child("isActive").getValue(Boolean.class);
                        isDeleted = isActive != null && !isActive;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking if lobby is active", e);
                }
                
                if (isDeleted) {
                    try {
                        listener.onLobbyDeleted();
                    } catch (Exception e) {
                        Log.e(TAG, "Error calling onLobbyDeleted", e);
                    }
                    return;
                }

                // Get players list
                try {
                    List<String> players = new ArrayList<>();
                    if (snapshot.child("players").exists()) {
                        for (com.google.firebase.database.DataSnapshot playerSnapshot : snapshot.child("players").getChildren()) {
                            if (playerSnapshot != null && playerSnapshot.getKey() != null) {
                                players.add(playerSnapshot.getKey());
                            }
                        }
                    }
                    listener.onPlayersChanged(players);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing players", e);
                }
                
                // Get host ID
                try {
                    if (snapshot.child("creator").exists()) {
                        String hostId = snapshot.child("creator").getValue(String.class);
                        if (hostId != null) {
                            listener.onHostChanged(hostId);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing host", e);
                }
                
                // Get lobby state
                try {
                    if (snapshot.child("state").exists()) {
                        String state = snapshot.child("state").getValue(String.class);
                        if (state != null) {
                            listener.onLobbyStateChanged(state);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing lobby state", e);
                }
                
                // Check if game has started - just log it for now
                try {
                    if (snapshot.child("gameStarted").exists()) {
                        Boolean gameStarted = snapshot.child("gameStarted").getValue(Boolean.class);
                        if (gameStarted != null && gameStarted) {
                            Log.d(TAG, "Game has started for lobby: " + lobbyId);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking game started status", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in onDataChange", e);
            }
        }

        @Override
        public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
            try {
                Log.e(TAG, "Lobby listener cancelled: " + (error != null ? error.getMessage() : "Unknown error"));
            } catch (Exception e) {
                Log.e(TAG, "Error in onCancelled", e);
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
                removeLobbyListener(lobbyId);
            } catch (Exception e) {
                Log.e(TAG, "Error removing existing listener", e);
            }
            
            // Create a named class instance instead of anonymous inner class
            SafeLobbyValueEventListener valueEventListener = new SafeLobbyValueEventListener(lobbyId, listener, TAG);
            
            // Add the listener to Firebase
            ValueEventListener registeredListener = lobbiesRef.child(lobbyId).addValueEventListener(valueEventListener);
            
            // Store the listener for later removal
            lobbyListeners.put(lobbyId, registeredListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add lobby listener", e);
        }
    }

    public void removeLobbyListener(String lobbyId) {
        ValueEventListener listener = lobbyListeners.remove(lobbyId);
        if (listener != null) {
            lobbiesRef.child(lobbyId).removeEventListener(listener);
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

    public interface LobbyListener {
        void onPlayersChanged(List<String> players);
        void onLobbyDeleted();
        void onHostChanged(String newHostId);
        void onLobbyStateChanged(String state);
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

    public void deleteLobby(String lobbyId) {
        if (getCurrentUser() == null) return;
        lobbiesRef.child(lobbyId).removeValue();
    }

    public void startGame(String lobbyId, OnCompleteListener<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        lobbiesRef.child(lobbyId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Map<String, Object> gameData = new HashMap<>();
            gameData.put("host", user.getUid());
            gameData.put("players", snapshot.child("players").getValue());
            gameData.put("startedAt", ServerValue.TIMESTAMP);

            inGameLobbiesRef.child(lobbyId).setValue(gameData)
                .addOnSuccessListener(aVoid -> {
                    lobbiesRef.child(lobbyId).removeValue();
                    if (callback != null) {
                        callback.onComplete(Tasks.forResult(null));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onComplete(Tasks.forException(e));
                    }
                });
        });
    }

    public void endGame(String lobbyId) {
        if (getCurrentUser() == null) return;
        inGameLobbiesRef.child(lobbyId).removeValue();
    }

    public interface GameStateListener {
        void onGameStarted(boolean started);
    }

    // Custom ValueEventListener class for game state to avoid anonymous inner class issues during dexing
    private static class SafeGameStateValueEventListener implements ValueEventListener {
        private final GameStateListener listener;
        private final String TAG;
        
        public SafeGameStateValueEventListener(GameStateListener listener, String tag) {
            this.listener = listener;
            this.TAG = tag;
        }
        
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            try {
                if (listener != null && snapshot != null) {
                    listener.onGameStarted(snapshot.exists());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in game state listener", e);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            try {
                Log.e(TAG, "Game listener cancelled", error != null ? error.toException() : new Exception("Unknown error"));
            } catch (Exception e) {
                Log.e(TAG, "Error in onCancelled", e);
            }
        }
    }
    
    public void addInGameLobbyListener(String lobbyId, GameStateListener listener) {
        try {
            if (lobbyId == null || listener == null || inGameLobbiesRef == null) {
                Log.e(TAG, "Invalid parameters in addInGameLobbyListener");
                return;
            }
            
            // Create a named class instance instead of anonymous inner class
            SafeGameStateValueEventListener valueEventListener = new SafeGameStateValueEventListener(listener, TAG);
            
            // Add the listener to Firebase
            inGameLobbiesRef.child(lobbyId).addValueEventListener(valueEventListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add game state listener", e);
        }
    }

    public void updateGameState(String lobbyId, Map<String, Object> updates) {
        if (getCurrentUser() == null) return;
        inGameLobbiesRef.child(lobbyId).updateChildren(updates);
    }
}
