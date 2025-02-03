package com.example.myt;

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

    public void addLobbyListener(String lobbyId, LobbyListener listener) {
        ValueEventListener valueEventListener = lobbiesRef.child(lobbyId)
            .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    if (!snapshot.exists() || 
                        (snapshot.child("isActive").exists() && 
                         !snapshot.child("isActive").getValue(Boolean.class))) {
                        listener.onLobbyDeleted();
                        return;
                    }

                    List<String> players = new ArrayList<>();
                    snapshot.child("players").getChildren().forEach(playerSnapshot -> 
                        players.add(playerSnapshot.getKey()));
                    
                    listener.onPlayersChanged(players);
                    
                    String hostId = snapshot.child("creator").getValue(String.class);
                    if (hostId != null) {
                        listener.onHostChanged(hostId);
                    }
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    Log.e(TAG, "Lobby listener cancelled", error.toException());
                }
            });
        
        lobbyListeners.put(lobbyId, valueEventListener);
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

    public void addInGameLobbyListener(String lobbyId, GameStateListener listener) {
        inGameLobbiesRef.child(lobbyId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onGameStarted(snapshot.exists());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Game listener cancelled", error.toException());
            }
        });
    }

    public void updateGameState(String lobbyId, Map<String, Object> updates) {
        if (getCurrentUser() == null) return;
        inGameLobbiesRef.child(lobbyId).updateChildren(updates);
    }
}
