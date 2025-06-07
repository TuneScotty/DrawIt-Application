package com.example.drawit_app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import android.util.Log;
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
    
    // isLocked now indicates if lobby has launched a game or is at full capacity
    // No longer used for password protection
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
    
    // Flag to track if this lobby is currently in an active game session
    // When true, lobby should be hidden from available lobbies list
    @Json(name = "inGame")
    private boolean inGame = false;

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
    
    // Track if we've logged lobby name getter/setter operations to reduce log spam
    private static boolean loggedNameOperations = false;
    
    public String getLobbyName() {
        return lobbyName;
    }
    
    public void setLobbyName(String lobbyName) {
        // Only log the first few times per session to avoid log spam
        if (!loggedNameOperations && Math.random() < 0.05) { // Log ~5% of calls
            android.util.Log.d("Lobby", "Lobby name set to: " + lobbyName);
            // After logging 3-5 times, stop logging these operations
            if (Math.random() < 0.3) loggedNameOperations = true;
        }
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
    
    /**
     * Check if this lobby is locked (either game started or capacity reached)
     * @return true if the lobby is locked, false otherwise
     */
    public boolean isLocked() {
        // A lobby is locked if it's in game or at max capacity
        return isLocked || inGame || (maxPlayers > 0 && players != null && players.size() >= maxPlayers);
    }
    
    /**
     * Set the locked status for this lobby
     * @param locked the new locked status
     */
    public void setLocked(boolean locked) {
        isLocked = locked;
        
        // If locking because game started, also set inGame flag
        if (locked) {
            // A lobby is typically locked when transitioning to the game
            Log.d("Lobby", "Lobby " + lobbyId + " is now locked");
        }
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
    
    /**
     * Checks if this lobby is currently in an active game session
     * @return true if the lobby has an active game, false otherwise
     */
    public boolean isInGame() {
        return inGame;
    }
    
    /**
     * Sets whether this lobby is currently in an active game session
     * When true, this lobby should be locked and filtered out from available lobbies list
     * @param inGame true if the lobby has started a game, false when game ends
     */
    public void setInGame(boolean inGame) {
        this.inGame = inGame;
        
        // When entering game, auto-lock the lobby to prevent new players from joining
        if (inGame) {
            this.isLocked = true;
            Log.d("Lobby", "Lobby " + lobbyId + " is now in game and locked");
        }
        
        // When game ends, automatically unlock the lobby if not at max capacity
        if (!inGame && players != null && players.size() < maxPlayers) {
            this.isLocked = false;
            Log.d("Lobby", "Lobby " + lobbyId + " game ended, lobby unlocked");
        }
    }
    
    public void addPlayer(User player) {
        // Check if lobby is already in game or at max capacity
        boolean isFull = (players != null && players.size() >= maxPlayers);
        
        if (!inGame && !isFull) {
            players.add(player);
            
            // Auto-lock when lobby reaches max capacity
            if (players.size() >= maxPlayers) {
                this.isLocked = true;
                Log.d("Lobby", "Lobby " + lobbyId + " is now full and locked");
            }
        } else {
            Log.d("Lobby", "Cannot add player to lobby " + lobbyId + 
                  ": inGame=" + inGame + ", isFull=" + isFull);
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
