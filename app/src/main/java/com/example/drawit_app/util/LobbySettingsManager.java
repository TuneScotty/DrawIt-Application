package com.example.drawit_app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.drawit_app.model.Lobby;

/**
 * Utility class to manage lobby settings locally
 * This is needed because the server doesn't store numRounds and roundDurationSeconds
 */
public class LobbySettingsManager {
    private static final String TAG = "LobbySettingsManager";
    
    // SharedPreferences keys
    private static final String PREF_NAME = "lobby_settings";
    private static final String KEY_ROUNDS_PREFIX = "rounds_";
    private static final String KEY_DURATION_PREFIX = "duration_";
    
    private final Context context;
    
    public LobbySettingsManager(Context context) {
        this.context = context;
    }
    
    /**
     * Store lobby settings locally for a specific lobby
     */
    public void storeSettings(String lobbyId, int numRounds, int roundDurationSeconds) {
        if (context == null || lobbyId == null || lobbyId.isEmpty()) {
            Log.e(TAG, "Cannot store settings - invalid context or lobbyId");
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Store both settings with lobby-specific keys
            editor.putInt(KEY_ROUNDS_PREFIX + lobbyId, numRounds);
            editor.putInt(KEY_DURATION_PREFIX + lobbyId, roundDurationSeconds);
            
            // Apply changes
            editor.apply();
            
            Log.d(TAG, "✅ Stored settings for lobby " + lobbyId + 
                 ": rounds=" + numRounds + ", duration=" + roundDurationSeconds);
        } catch (Exception e) {
            Log.e(TAG, "Failed to store lobby settings", e);
        }
    }
    
    /**
     * Retrieve and apply stored settings to a lobby object
     * Returns true if settings were applied
     */
    public void applySettings(Lobby lobby) {
        if (lobby == null || lobby.getLobbyId().isEmpty() || context == null) {
            Log.w(TAG, "Cannot apply settings - invalid lobby or context");
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String lobbyId = lobby.getLobbyId();
            
            // Log the lobby's current settings before applying stored ones
            Log.d(TAG, "Lobby settings before applying stored settings: " + 
                  "id=" + lobbyId + 
                  ", rounds=" + lobby.getNumRounds() + 
                  ", duration=" + lobby.getRoundDurationSeconds());
            
            // Check if we have stored settings for this lobby
            boolean hasStoredRounds = prefs.contains(KEY_ROUNDS_PREFIX + lobbyId);
            boolean hasStoredDuration = prefs.contains(KEY_DURATION_PREFIX + lobbyId);
            
            if (hasStoredRounds || hasStoredDuration) {
                // Get stored settings with defaults from the lobby object
                int numRounds = prefs.getInt(KEY_ROUNDS_PREFIX + lobbyId, lobby.getNumRounds());
                int roundDuration = prefs.getInt(KEY_DURATION_PREFIX + lobbyId, lobby.getRoundDurationSeconds());
                
                boolean changed = false;
                
                // Only override if values are reasonable
                if (numRounds > 0 && hasStoredRounds) {
                    lobby.setNumRounds(numRounds);
                    changed = true;
                }
                
                if (roundDuration > 0 && hasStoredDuration) {
                    lobby.setRoundDurationSeconds(roundDuration);
                    changed = true;
                }
                
                if (changed) {
                    Log.d(TAG, "✅ Applied stored settings to lobby " + lobbyId + 
                         ": rounds=" + numRounds + ", duration=" + roundDuration);
                } else {
                    Log.d(TAG, "No changes made when applying settings to lobby " + lobbyId);
                }
            } else {
                Log.d(TAG, "No stored settings found for lobby " + lobbyId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying stored settings", e);
        }

    }
    
    /**
     * Clear settings for a specific lobby
     */
    public void clearSettings(String lobbyId) {
        if (context == null || lobbyId == null || lobbyId.isEmpty()) {
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            editor.remove(KEY_ROUNDS_PREFIX + lobbyId);
            editor.remove(KEY_DURATION_PREFIX + lobbyId);
            editor.apply();
            
            Log.d(TAG, "Cleared settings for lobby " + lobbyId);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing settings", e);
        }
    }
}
