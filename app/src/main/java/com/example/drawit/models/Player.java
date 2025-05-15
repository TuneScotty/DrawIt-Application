package com.example.drawit.models;

/**
 * Represents a player in the DrawIt application.
 * This unified Player model is used throughout the application for both lobby and game functionality.
 */
public class Player {
    // Basic player identification
    private String id;      // Firebase UID of the player
    private String username;  // Display name of the player
    private long joinedAt;  // Timestamp of when the player joined
    
    // Game-specific properties
    private int score;      // Player's score in the game
    private boolean isHost; // Whether the player is the host of the game/lobby
    private boolean isDrawing; // Whether the player is currently drawing
    private boolean hasGuessedCorrectly; // Whether the player has guessed correctly in the current round
    private boolean isConnected; // Whether the player is currently connected
    private long disconnectionTime; // Timestamp of when the player disconnected
    private boolean isMuted; // Whether the player is muted
    
    /**
     * Default constructor required for Firebase deserialization
     */
    public Player() {
        // Initialize with default values
        this.score = 0;
        this.isHost = false;
        this.isDrawing = false;
        this.hasGuessedCorrectly = false;
        this.isConnected = true;
        this.isMuted = false;
        this.username = "Player"; // Default username
    }
    
    /**
     * Constructor with player ID
     */
    public Player(String id) {
        this();
        this.id = id;
    }
    
    /**
     * Constructor with player ID and username
     */
    public Player(String id, String username) {
        this();
        this.id = id;
        this.username = username;
    }
    
    // Basic getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    // Alias for username to maintain compatibility with game code
    public String getName() {
        return username;
    }
    
    public void setName(String name) {
        this.username = name;
    }
    
    public long getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    // Game-specific getters and setters
    public int getScore() {
        return score;
    }
    
    public void setScore(int score) {
        this.score = score;
    }
    
    /**
     * Add points to the player's score
     */
    public void addPoints(int points) {
        this.score += points;
    }
    
    public boolean isHost() {
        return isHost;
    }
    
    public void setHost(boolean host) {
        isHost = host;
    }
    
    public boolean isDrawing() {
        return isDrawing;
    }
    
    public void setDrawing(boolean drawing) {
        isDrawing = drawing;
    }
    
    public boolean isHasGuessedCorrectly() {
        return hasGuessedCorrectly;
    }
    
    public void setHasGuessedCorrectly(boolean hasGuessedCorrectly) {
        this.hasGuessedCorrectly = hasGuessedCorrectly;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void setConnected(boolean connected) {
        this.isConnected = connected;
        if (!connected) {
            this.disconnectionTime = System.currentTimeMillis();
        }
    }
    
    public long getDisconnectionTime() {
        return disconnectionTime;
    }
    
    public void setDisconnectionTime(long disconnectionTime) {
        this.disconnectionTime = disconnectionTime;
    }
    
    public boolean isMuted() {
        return isMuted;
    }
    
    public void setMuted(boolean muted) {
        isMuted = muted;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Player player = (Player) o;
        return id != null ? id.equals(player.id) : player.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
