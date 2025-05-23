package com.example.drawit.models;

import java.io.Serializable;

/**
 * Model class representing game settings.
 * Provides configuration parameters for a game session.
 */
public class GameSettings implements Serializable {
    // Constants for default, min and max values
    public static final int DEFAULT_ROUNDS = 3;
    public static final int MIN_ROUNDS = 1;
    public static final int MAX_ROUNDS = 10;
    
    public static final int DEFAULT_ROUND_DURATION_SECONDS = 120;
    public static final int MIN_ROUND_DURATION_SECONDS = 30;
    public static final int MAX_ROUND_DURATION_SECONDS = 300;
    
    // Core settings
    private int numberOfRounds;
    private int roundDurationSeconds;
    private boolean enableWordCustomization;
    private boolean showScoresDuringGame;
    
    /**
     * Default constructor with default values
     */
    public GameSettings() {
        this.numberOfRounds = DEFAULT_ROUNDS;
        this.roundDurationSeconds = DEFAULT_ROUND_DURATION_SECONDS;
        this.enableWordCustomization = false;
        this.showScoresDuringGame = true;
    }
    
    /**
     * Constructor with specific values
     */
    public GameSettings(int numberOfRounds, int roundDurationSeconds, 
                        boolean enableWordCustomization, boolean showScoresDuringGame) {
        setNumberOfRounds(numberOfRounds);
        setRoundDurationSeconds(roundDurationSeconds);
        this.enableWordCustomization = enableWordCustomization;
        this.showScoresDuringGame = showScoresDuringGame;
    }
    
    /**
     * Get the number of rounds for the game
     * @return Number of rounds
     */
    public int getNumberOfRounds() {
        return numberOfRounds;
    }
    
    /**
     * Set the number of rounds, with validation
     * @param numberOfRounds Number of rounds (will be clamped to valid range)
     */
    public void setNumberOfRounds(int numberOfRounds) {
        this.numberOfRounds = Math.max(MIN_ROUNDS, Math.min(MAX_ROUNDS, numberOfRounds));
    }
    
    /**
     * Get the round duration in seconds
     * @return Duration in seconds
     */
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    /**
     * Set the round duration, with validation
     * @param roundDurationSeconds Duration in seconds (will be clamped to valid range)
     */
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = Math.max(MIN_ROUND_DURATION_SECONDS, 
                                            Math.min(MAX_ROUND_DURATION_SECONDS, roundDurationSeconds));
    }
    
    /**
     * Check if word customization is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnableWordCustomization() {
        return enableWordCustomization;
    }
    
    /**
     * Set word customization option
     * @param enableWordCustomization true to enable, false to disable
     */
    public void setEnableWordCustomization(boolean enableWordCustomization) {
        this.enableWordCustomization = enableWordCustomization;
    }
    
    /**
     * Check if scores should be shown during the game
     * @return true if scores should be shown, false otherwise
     */
    public boolean isShowScoresDuringGame() {
        return showScoresDuringGame;
    }
    
    /**
     * Set whether scores should be shown during the game
     * @param showScoresDuringGame true to show scores, false to hide
     */
    public void setShowScoresDuringGame(boolean showScoresDuringGame) {
        this.showScoresDuringGame = showScoresDuringGame;
    }
}
