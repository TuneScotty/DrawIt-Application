package com.example.drawit_app.api.message;

import android.util.Log;

import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket message for lobby state updates
 */
public class LobbyStateMessage extends WebSocketMessage {
    private static final String TAG = "LobbyStateMessage";
    
    /**
     * Default constructor for Moshi deserialization
     */
    public LobbyStateMessage() {
        super("lobby_state", null);
    }
    
    /**
     * Safely retrieves the payload as a LobbyPayload object
     * Handles conversion from Map if necessary to prevent ClassCastException
     */
    public LobbyPayload getLobbyPayload() {
        Object rawPayload = getPayload();
        
        // Direct cast if already correct type
        if (rawPayload instanceof LobbyPayload) {
            return (LobbyPayload) rawPayload;
        }
        
        // Handle case where payload is a Map (from Moshi deserialization)
        if (rawPayload instanceof Map) {
            try {
                Map<?, ?> payloadMap = (Map<?, ?>) rawPayload;
                LobbyPayload payload = new LobbyPayload();
                
                // Extract lobby data
                if (payloadMap.containsKey("lobby")) {
                    Object lobbyObj = payloadMap.get("lobby");
                    if (lobbyObj instanceof Map) {
                        Lobby lobby = convertMapToLobby((Map<?, ?>) lobbyObj);
                        payload.setLobby(lobby);
                    } else if (lobbyObj instanceof Lobby) {
                        payload.setLobby((Lobby) lobbyObj);
                    }
                } else {
                    // Create an empty Lobby to prevent NPE
                    payload.setLobby(new Lobby());
                }
                
                // Extract host user
                if (payloadMap.containsKey("hostUser")) {
                    Object hostObj = payloadMap.get("hostUser");
                    if (hostObj instanceof Map) {
                        User hostUser = convertMapToUser((Map<?, ?>) hostObj);
                        payload.setHostUser(hostUser);
                    } else if (hostObj instanceof User) {
                        payload.setHostUser((User) hostObj);
                    }
                }
                
                // Extract players list
                if (payloadMap.containsKey("players")) {
                    Object playersObj = payloadMap.get("players");
                    if (playersObj instanceof List) {
                        List<?> playersList = (List<?>) playersObj;
                        List<User> players = new ArrayList<>();
                        
                        for (Object playerObj : playersList) {
                            if (playerObj instanceof Map) {
                                User player = convertMapToUser((Map<?, ?>) playerObj);
                                if (player != null) {
                                    players.add(player);
                                }
                            } else if (playerObj instanceof User) {
                                players.add((User) playerObj);
                            }
                        }
                        payload.setPlayers(players);
                    }
                } else {
                    // Initialize with empty list to avoid NPE
                    payload.setPlayers(new ArrayList<>());
                }
                
                // Extract event and user_id
                if (payloadMap.containsKey("event")) {
                    payload.setEvent(payloadMap.get("event").toString());
                } else {
                    payload.setEvent("unknown"); // Default event
                }
                
                if (payloadMap.containsKey("user_id")) {
                    payload.setUserId(payloadMap.get("user_id").toString());
                }
                
                return payload;
            } catch (Exception e) {
                Log.e(TAG, "Error converting Map to LobbyPayload: " + e.getMessage(), e);
                return createDefaultPayload();
            }
        }
        
        // If we reach here, the payload is neither a LobbyPayload nor a Map
        // Log the issue and return a default payload
        Log.e(TAG, "Payload is neither LobbyPayload nor Map: " + 
              (rawPayload != null ? rawPayload.getClass().getName() : "null"));
        return createDefaultPayload();
    }
    
    /**
     * Creates a default LobbyPayload to prevent null pointer exceptions
     */
    private LobbyPayload createDefaultPayload() {
        LobbyPayload payload = new LobbyPayload();
        payload.setLobby(new Lobby());
        payload.setPlayers(new ArrayList<>());
        payload.setEvent("unknown");
        Log.d(TAG, "Created default LobbyPayload to prevent NPE");
        return payload;
    }
    
    public void setLobbyPayload(LobbyPayload payload) {
        setPayload(payload);
    }
    
    /**
     * Helper method to convert a Map to a Lobby object
     */
    private Lobby convertMapToLobby(Map<?, ?> map) {
        if (map == null) return null;
        
        Lobby lobby = new Lobby();
        
        if (map.containsKey("lobbyId")) {
            lobby.setLobbyId(map.get("lobbyId").toString());
        }
        
        if (map.containsKey("name")) {
            lobby.setLobbyName(map.get("name").toString());
        }
        
        if (map.containsKey("hostId")) {
            lobby.setHostId(map.get("hostId").toString());
        }
        
        if (map.containsKey("maxPlayers") && map.get("maxPlayers") instanceof Number) {
            lobby.setMaxPlayers(((Number) map.get("maxPlayers")).intValue());
        }
        
        // Note: Lobby doesn't have setPrivate method, using isLocked instead
        if (map.containsKey("isPrivate") && map.get("isPrivate") instanceof Boolean) {
            // If private is true, we set locked to true
            if ((Boolean) map.get("isPrivate")) {
                lobby.setLocked(true);
            }
        }
        
        if (map.containsKey("isLocked") && map.get("isLocked") instanceof Boolean) {
            lobby.setLocked((Boolean) map.get("isLocked"));
        }
        
        if (map.containsKey("numRounds") && map.get("numRounds") instanceof Number) {
            lobby.setNumRounds(((Number) map.get("numRounds")).intValue());
        }
        
        if (map.containsKey("roundDurationSeconds") && map.get("roundDurationSeconds") instanceof Number) {
            lobby.setRoundDurationSeconds(((Number) map.get("roundDurationSeconds")).intValue());
        }
        
        // Note: Lobby doesn't have setPlayerCount method, this is calculated from players list
        
        return lobby;
    }
    
    /**
     * Helper method to convert a Map to a User object
     */
    private User convertMapToUser(Map<?, ?> map) {
        if (map == null) return null;
        
        User user = new User();
        
        // Note: User doesn't have setId method, using userId instead
        if (map.containsKey("_id")) {
            user.setUserId(map.get("_id").toString());
        }
        
        if (map.containsKey("userId")) {
            user.setUserId(map.get("userId").toString());
        }
        
        if (map.containsKey("username")) {
            user.setUsername(map.get("username").toString());
        }
        
        if (map.containsKey("avatarUrl")) {
            user.setAvatarUrl(map.get("avatarUrl").toString());
        }
        
        return user;
    }
    
    /**
     * Payload containing lobby state information
     */
    public static class LobbyPayload {
        @Json(name = "lobby")
        private Lobby lobby;
        
        @Json(name = "players")
        private List<User> players;
        
        @Json(name = "hostUser")
        private User hostUser; // Host user details at payload level
        
        @Json(name = "event")
        private String event; // "joined", "left", "locked", "unlocked", "settings_updated"
        
        @Json(name = "user_id")
        private String userId; // ID of the user who triggered the event
        
        public LobbyPayload() {
        }
        
        public Lobby getLobby() {
            return lobby;
        }
        
        public void setLobby(Lobby lobby) {
            this.lobby = lobby;
        }
        
        public List<User> getPlayers() {
            return players;
        }
        
        public void setPlayers(List<User> players) {
            this.players = players;
        }
        
        public User getHostUser() {
            return hostUser;
        }
        
        public void setHostUser(User hostUser) {
            this.hostUser = hostUser;
        }
        
        public String getEvent() {
            return event;
        }
        
        public void setEvent(String event) {
            this.event = event;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
