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
    
    @Json(name = "numRounds")
    private int numRounds;
    
    @Json(name = "roundDurationSeconds")
    private int roundDurationSeconds;
    
    // Transient fields not saved in database
    private transient List<User> players;
    
    public Lobby() {
        this.players = new ArrayList<>();
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
        return lobbyName;
    }
    
    public void setLobbyName(String lobbyName) {
        this.lobbyName = lobbyName;
    }
    
    /**
     * Convenience method for getting lobby name (used by adapter)
     */
    public String getName() {
        return lobbyName;
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
    public User getHost() {
        // Check if hostId is valid
        if (hostId == null || hostId.isEmpty()) {
            return null;
        }
        
        // Try to find the host in the players list first
        if (players != null && !players.isEmpty()) {
            // Find the host in the players list by ID
            for (User user : players) {
                if (user != null && user.getUserId() != null && 
                    user.getUserId().equals(hostId) && 
                    user.getUsername() != null && !user.getUsername().isEmpty()) {
                    // Found host with complete info including username
                    return user;
                }
            }
        }
        
        // If we couldn't find the host with complete info in the players list,
        // create a user with a placeholder username based on ID
        User hostUser = new User();
        hostUser.setUserId(hostId);
        
        // Set a username that clearly indicates this is a host
        // Instead of a cryptic "Player-tb72", use a clearer name
        try {
            if (hostId != null && !hostId.isEmpty()) {
                hostUser.setUsername("Host #" + hostId.substring(0, Math.min(6, hostId.length())));
            } else {
                hostUser.setUsername("Unknown Host");
            }
        } catch (Exception e) {
            // Catch any string manipulation exceptions
            android.util.Log.e("Lobby", "Error setting host username: " + e.getMessage());
            hostUser.setUsername("Host");
        }
        
        return hostUser;
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
        this.numRounds = numRounds;
    }
    
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = roundDurationSeconds;
    }
    
    public List<User> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<User> players) {
        this.players = players;
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
    
    /**
     * Assigns a new host if the current host leaves
     * @param excludeUserId User ID to exclude (usually the leaving host)
     * @return true if a new host was assigned, false if the lobby should be disbanded
     */
    public boolean assignNewHostIfNeeded(String excludeUserId) {
        if (!hostId.equals(excludeUserId)) {
            return true; // Current host hasn't left
        }
        
        // If there are other players, assign the first one as the new host
        for (User user : players) {
            if (!user.getUserId().equals(excludeUserId)) {
                hostId = user.getUserId();
                return true;
            }
        }
        
        return false; // No other players, should disband lobby
    }
}
