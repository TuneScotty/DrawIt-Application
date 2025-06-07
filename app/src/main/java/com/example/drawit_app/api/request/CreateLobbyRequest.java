package com.example.drawit_app.network.request;

import com.squareup.moshi.Json;

/**
 * Request model for creating a new lobby
 */
public class CreateLobbyRequest {
    // Use exact field names as expected by the server
    private String name; // Changed from lobbyName to name as required by the server
    private int maxPlayers;
    
    // Note: Based on the lobby JSON structure from the server, we need to use these exact field names
    @Json(name = "numRounds")
    private int numRounds;
    
    @Json(name = "roundDurationSeconds")
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

    // Host getter and setter methods removed as the host field is no longer used
    
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
