package com.example.drawit_app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Relation;

import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a game lobby where players gather before starting a game.
 */
@Entity(tableName = "lobbies")
public class Lobby {

    @NonNull
    @PrimaryKey
    @Json(name = "lobbyId")
    private String lobbyId;
    
    @Json(name = "name")
    private String lobbyName;
    
    @Json(name = "hostId")
    private String hostId;
    
    @Json(name = "isLocked")
    private boolean isLocked;
    
    @Json(name = "maxPlayers")
    private int maxPlayers;
    
    // Server sometimes doesn't return these values in responses despite accepting them in requests
    // Use multiple possible JSON field names to improve deserialization success
    @Json(name = "numRounds")
    private int numRounds = 3; // Default value
    
    @Json(name = "roundDurationSeconds")
    private int roundDurationSeconds = 60; // Default value
    
    // Track if settings were explicitly set to determine if we should use defaults
    private transient boolean numRoundsExplicitlySet = false;
    private transient boolean roundDurationExplicitlySet = false;
    private transient boolean hasLocalSettingsOverride = false;

    @Ignore
    @Json(name = "players")
    private List<User> players;
    
    // Host user details from server
    @Ignore
    @Json(name = "hostUser")
    private User hostUser;
    
    public Lobby() {
        this.players = new ArrayList<>(); // Initialize to prevent null pointer exceptions
        lobbyId = "";
    }
    
    @Ignore
    public Lobby(@NonNull String lobbyId, String lobbyName, String hostId, int maxPlayers,
                int numRounds, int roundDurationSeconds) {
        this.lobbyId = lobbyId;
        this.lobbyName = lobbyName;
        this.hostId = hostId;
        this.isLocked = false;
        this.maxPlayers = maxPlayers;
        this.numRounds = numRounds;
        this.roundDurationSeconds = roundDurationSeconds;
        this.players = new ArrayList<>();
    }
    
    @NonNull
    public String getLobbyId() {
        return lobbyId;
    }
    
    public void setLobbyId(@NonNull String lobbyId) {
        this.lobbyId = lobbyId;
    }
    
    public String getLobbyName() {
        android.util.Log.d("Lobby", "DEBUG: getLobbyName() called, returning: " + lobbyName);
        return lobbyName;
    }
    
    public void setLobbyName(String lobbyName) {
        android.util.Log.d("Lobby", "DEBUG: setLobbyName() called with: " + lobbyName + ", caller: " + new Exception().getStackTrace()[1]);
        this.lobbyName = lobbyName;
    }

    /**
     * Convenience method for getting lobby ID (used by fragments)
     */
    public String getId() {
        return lobbyId;
    }
    
    public String getHostId() {
        return hostId;
    }
    
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }
    
    /**
     * Get the host user object
     * @return User object for the host, or null if not found
     */
    public List<User> getPlayers() {
        if (this.players == null) { // Defensive null check
            this.players = new ArrayList<>();
        }
        return this.players;
    }

    public void setPlayers(List<User> players) {
        this.players = players;
    }

    public User getHost() {
        // First priority: use the hostUser object if available
        if (hostUser != null) {
            return hostUser;
        }
        
        // Second priority: if no hostUser but we have hostId
        if (hostId == null || hostId.isEmpty()) {
            return null;
        }
        
        // Try to find the host in the players list
        if (players != null) {
            for (User player : players) {
                if (player != null && hostId.equals(player.getUserId())) {
                    return player;
                }
            }
        }
        
        // If host not found in players list or hostUser field
        // create a basic User object with only the ID set
        User basicHostUser = new User();
        basicHostUser.setUserId(hostId);
        return basicHostUser;
    }
    
    public User getHostUser() {
        return hostUser;
    }
    
    public void setHostUser(User hostUser) {
        this.hostUser = hostUser;
    }
    
    /**
     * Convenience method for getting number of rounds (used by fragments)
     */
    public int getRounds() {
        return numRounds;
    }
    
    /**
     * Convenience method for getting round duration (used by fragments)
     * @return Round duration in seconds
     */
    public int getRoundDuration() {
        return roundDurationSeconds;
    }
    
    public boolean isLocked() {
        return isLocked;
    }
    
    public void setLocked(boolean locked) {
        isLocked = locked;
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
        if (numRounds > 0) {
            this.numRounds = numRounds;
            this.numRoundsExplicitlySet = true;
            this.hasLocalSettingsOverride = true;
        }
    }
    
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        if (roundDurationSeconds > 0) {
            this.roundDurationSeconds = roundDurationSeconds;
            this.roundDurationExplicitlySet = true;
            this.hasLocalSettingsOverride = true;
        }
    }
    
    public void addPlayer(User player) {
        if (players.size() < maxPlayers && !isLocked) {
            players.add(player);
        }
    }
    
    public void removePlayer(String userId) {
        players.removeIf(user -> user.getUserId().equals(userId));
    }
    
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }
    
    public boolean isUserInLobby(String userId) {
        for (User user : players) {
            if (user.getUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }
}
