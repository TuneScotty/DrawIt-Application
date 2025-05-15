package com.example.drawit.game.models;

/**
 * Represents a guess made by a player during the drawing phase.
 * Tracks the player, the guess text, and the timestamp.
 */
public class Guess {
    private String playerId;
    private String playerName;
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
    public Guess(String playerId, String playerName, String guessText) {
        this();
        this.playerId = playerId;
        this.playerName = playerName;
        this.guessText = guessText;
    }

    public Guess(String playerId, String guessText, long l) {
    }

    // Getters and setters
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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
