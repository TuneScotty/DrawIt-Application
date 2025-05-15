package com.example.drawit.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lobby {
    private String id;
    private String name;
    private String creator;
    private int playersCount;
    private String password;
    private boolean active;
    private Map<String, Object> players;
    private Boolean gameStarted; // Using Boolean object to allow null checks
    
    /**
     * Default no-argument constructor required for Firebase deserialization
     */
    public Lobby() {
        // Initialize with default values
        this.active = true;
        this.players = new HashMap<>();
    }

    public Lobby(String id, String name, String creator, int playersCount, String password) {
        this.id = id;
        this.name = name;
        this.creator = creator;
        this.playersCount = playersCount;
        this.password = password;
        this.players = new HashMap<>();
    }

    public String getId() { return id; }

    public void setId(String id) { 
        this.id = id;
    }

    public String getName() { return name; }
    public String getCreator() { return creator; }
    public int getPlayersCount() { return playersCount; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean hasPassword() {
        return this.password != null && !this.password.trim().isEmpty();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> getPlayers() {
        return players;
    }

    public void setPlayers(Map<String, Object> players) {
        this.players = players;
    }
    
    public Boolean getGameStarted() {
        return gameStarted;
    }
    
    public void setGameStarted(Boolean gameStarted) {
        this.gameStarted = gameStarted;
    }
}