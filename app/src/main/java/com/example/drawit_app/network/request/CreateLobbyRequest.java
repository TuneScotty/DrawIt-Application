package com.example.drawit_app.network.request;

/**
 * Request model for creating a new lobby
 */
public class CreateLobbyRequest {
    // Use exact field names as expected by the server
    private String name; // Changed from lobbyName to name as required by the server
    private int maxPlayers;
    private int numRounds;
    private int roundDurationSeconds;
    
    public CreateLobbyRequest(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        this.name = lobbyName; // Store the lobbyName as name for the server
        this.maxPlayers = maxPlayers;
        this.numRounds = numRounds;
        this.roundDurationSeconds = roundDurationSeconds;
    }
    
    // Keeping both getter methods for backward compatibility
    public String getName() {
        return name;
    }
    
    public String getLobbyName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setLobbyName(String lobbyName) {
        this.name = lobbyName;
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
    
    public int getNumRounds() {
        return numRounds;
    }
    
    public void setNumRounds(int numRounds) {
        this.numRounds = numRounds;
    }
    
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = roundDurationSeconds;
    }
}
