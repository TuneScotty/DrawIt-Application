package com.example.drawit.game.models;

/**
 * Represents a guess made by a player during the drawing phase.
 * Tracks the player, the guess text, and the timestamp.
 */
public class Guess {
    private String playerId;
    private String guessText;
    private long timestamp;
    private boolean isCorrect;
    
    /**
     * Default constructor for Firebase
     */
    public Guess() {
        this.timestamp = System.currentTimeMillis();
        this.isCorrect = false;
    }
    
    /**
     * Constructor with guess details
     */
    public Guess(String playerId, String guessText) {
        this();
        this.playerId = playerId;
        this.guessText = guessText;
    }

    public Guess(String playerId, String guessText, long timestamp) {
        this.playerId = playerId;
        this.guessText = guessText;
        this.timestamp = timestamp;
        this.isCorrect = false;
    }

    // Getters and setters
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public String getGuessText() {
        return guessText;
    }
    
    public void setGuessText(String guessText) {
        this.guessText = guessText;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isCorrect() {
        return isCorrect;
    }
    
    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }
}
