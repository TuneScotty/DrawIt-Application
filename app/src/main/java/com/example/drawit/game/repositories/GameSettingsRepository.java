package com.example.drawit.game.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.drawit.models.GameSettings;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Repository pattern implementation for game settings.
 * Manages persistence and retrieval of game settings from Firebase.
 */
public class GameSettingsRepository {
    private static final String TAG = "GameSettingsRepository";
    private static final String SETTINGS_PATH = "lobbies/%s/settings";
    
    private final DatabaseReference databaseReference;
    
    /**
     * Interface for settings operation callbacks
     */
    public interface SettingsCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }
    
    /**
     * Constructor initializing Firebase reference
     */
    public GameSettingsRepository() {
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
    }
    
    /**
     * Save game settings for a specific lobby
     * 
     * @param lobbyId The lobby ID
     * @param settings The settings to save
     * @param callback Callback for operation result
     */
    public void saveSettings(String lobbyId, GameSettings settings, SettingsCallback<Void> callback) {
        if (lobbyId == null || lobbyId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Lobby ID cannot be null or empty"));
            return;
        }
        
        String path = String.format(SETTINGS_PATH, lobbyId);
        databaseReference.child(path).setValue(settings)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Settings saved successfully for lobby: " + lobbyId);
                    callback.onSuccess(null);
                } else {
                    Log.e(TAG, "Error saving settings for lobby: " + lobbyId, task.getException());
                    callback.onError(task.getException());
                }
            });
    }
    
    /**
     * Get game settings for a specific lobby
     * 
     * @param lobbyId The lobby ID
     * @param callback Callback for retrieved settings
     */
    public void getSettings(String lobbyId, SettingsCallback<GameSettings> callback) {
        if (lobbyId == null || lobbyId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Lobby ID cannot be null or empty"));
            return;
        }
        
        String path = String.format(SETTINGS_PATH, lobbyId);
        databaseReference.child(path).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot != null && snapshot.exists()) {
                    GameSettings settings = snapshot.getValue(GameSettings.class);
                    if (settings != null) {
                        Log.d(TAG, "Settings retrieved successfully for lobby: " + lobbyId);
                        callback.onSuccess(settings);
                    } else {
                        Log.d(TAG, "No settings found for lobby: " + lobbyId + ", using defaults");
                        callback.onSuccess(new GameSettings());
                    }
                } else {
                    Log.d(TAG, "No settings found for lobby: " + lobbyId + ", using defaults");
                    callback.onSuccess(new GameSettings());
                }
            } else {
                Log.e(TAG, "Error getting settings for lobby: " + lobbyId, task.getException());
                callback.onError(task.getException());
            }
        });
    }
    
    /**
     * Listen for real-time settings changes
     * 
     * @param lobbyId The lobby ID
     * @param callback Callback for updated settings
     * @return The ValueEventListener for later removal
     */
    public ValueEventListener listenForSettingsChanges(String lobbyId, SettingsCallback<GameSettings> callback) {
        if (lobbyId == null || lobbyId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Lobby ID cannot be null or empty"));
            return null;
        }
        
        String path = String.format(SETTINGS_PATH, lobbyId);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    GameSettings settings = snapshot.getValue(GameSettings.class);
                    if (settings != null) {
                        Log.d(TAG, "Settings updated for lobby: " + lobbyId);
                        callback.onSuccess(settings);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Settings listener cancelled: " + error.getMessage());
                callback.onError(error.toException());
            }
        };
        
        databaseReference.child(path).addValueEventListener(listener);
        return listener;
    }
    
    /**
     * Remove settings change listener
     * 
     * @param lobbyId The lobby ID
     * @param listener The listener to remove
     */
    public void removeSettingsListener(String lobbyId, ValueEventListener listener) {
        if (lobbyId == null || lobbyId.isEmpty() || listener == null) {
            return;
        }
        
        String path = String.format(SETTINGS_PATH, lobbyId);
        databaseReference.child(path).removeEventListener(listener);
    }
}
