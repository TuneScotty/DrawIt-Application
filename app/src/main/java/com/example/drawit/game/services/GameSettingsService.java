package com.example.drawit.game.services;

import android.util.Log;

import com.example.drawit.game.repositories.GameSettingsRepository;
import com.example.drawit.models.GameSettings;

/**
 * Service to manage game settings application throughout the game lifecycle.
 * Acts as a mediator between the repository and game components.
 */
public class GameSettingsService {
    private static final String TAG = "GameSettingsService";
    private static GameSettingsService instance;
    
    private final GameSettingsRepository repository;
    private GameSettings cachedSettings;
    
    /**
     * Get the singleton instance of GameSettingsService
     * @return GameSettingsService instance
     */
    public static synchronized GameSettingsService getInstance() {
        if (instance == null) {
            instance = new GameSettingsService();
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private GameSettingsService() {
        this.repository = new GameSettingsRepository();
        this.cachedSettings = new GameSettings(); // Default settings
    }
    
    /**
     * Get game settings for a specific lobby
     * @param lobbyId The lobby ID
     * @param callback Callback with the settings
     */
    public void getGameSettings(String lobbyId, GameSettingsCallback callback) {
        repository.getSettings(lobbyId, new GameSettingsRepository.SettingsCallback<GameSettings>() {
            @Override
            public void onSuccess(GameSettings settings) {
                cachedSettings = settings;
                callback.onSettingsLoaded(settings);
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading settings", e);
                callback.onSettingsError(e);
            }
        });
    }
    
    /**
     * Apply game settings to the current game instance
     * @param game The game instance to apply settings to
     * @param lobbyId The lobby ID
     * @param callback Optional callback to handle completion
     */
    public void applySettingsToGame(Object game, String lobbyId, ApplySettingsCallback callback) {
        getGameSettings(lobbyId, new GameSettingsCallback() {
            @Override
            public void onSettingsLoaded(GameSettings settings) {
                try {
                    // Apply settings to game object
                    if (game != null) {
                        // Apply round count
                        applyRoundCount(game, settings.getNumberOfRounds());
                        
                        // Apply round duration
                        applyRoundDuration(game, settings.getRoundDurationSeconds());
                        
                        // Apply other settings as needed
                        Log.d(TAG, "Applied settings to game: rounds=" + settings.getNumberOfRounds() + 
                               ", duration=" + settings.getRoundDurationSeconds() + "s");
                    }
                    
                    if (callback != null) {
                        callback.onSettingsApplied(settings);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error applying settings to game", e);
                    if (callback != null) {
                        callback.onSettingsApplyError(e);
                    }
                }
            }
            
            @Override
            public void onSettingsError(Exception e) {
                Log.e(TAG, "Error retrieving settings", e);
                if (callback != null) {
                    callback.onSettingsApplyError(e);
                }
            }
        });
    }
    
    /**
     * Apply round count setting to game
     */
    private void applyRoundCount(Object game, int roundCount) {
        // Game-specific logic to set round count
        // This will be different based on your Game class implementation
        try {
            if (game instanceof com.example.drawit.game.models.Game) {
                ((com.example.drawit.game.models.Game) game).setTotalRounds(roundCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying round count", e);
        }
    }
    
    /**
     * Apply round duration setting to game
     */
    private void applyRoundDuration(Object game, int durationSeconds) {
        // Game-specific logic to set round duration
        // This will be different based on your Game class implementation
        try {
            if (game instanceof com.example.drawit.game.models.Game) {
                ((com.example.drawit.game.models.Game) game).setRoundDurationSeconds(durationSeconds);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying round duration", e);
        }
    }
    
    /**
     * Get the cached settings (last loaded)
     * @return The cached settings or default settings
     */
    public GameSettings getCachedSettings() {
        return cachedSettings;
    }
    
    /**
     * Interface for settings load callbacks
     */
    public interface GameSettingsCallback {
        void onSettingsLoaded(GameSettings settings);
        void onSettingsError(Exception e);
    }
    
    /**
     * Interface for settings application callbacks
     */
    public interface ApplySettingsCallback {
        void onSettingsApplied(GameSettings settings);
        void onSettingsApplyError(Exception e);
    }
}
