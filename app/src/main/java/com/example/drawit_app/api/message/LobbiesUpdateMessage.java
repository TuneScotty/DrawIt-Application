package com.example.drawit_app.api.message;

import android.util.Log;

import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket message for lobbies update notifications
 */
public class LobbiesUpdateMessage extends WebSocketMessage {
    private static final String TAG = "LobbiesUpdateMessage";
    
    public static final String TYPE_LOBBIES_UPDATE = "lobbies_update";
    
    public LobbiesUpdateMessage() {
        super(TYPE_LOBBIES_UPDATE, null);
    }
    
    /**
     * Safely retrieves the payload as a LobbiesUpdatePayload object
     * Handles conversion from Map if necessary to prevent ClassCastException
     */
    public LobbiesUpdatePayload getLobbiesPayload() {
        Object rawPayload = getPayload();
        
        // If payload is already the correct type, return it
        if (rawPayload instanceof LobbiesUpdatePayload) {
            return (LobbiesUpdatePayload) rawPayload;
        }
        
        // If payload is a Map (what Moshi often creates), convert it
        if (rawPayload instanceof Map<?, ?>) {
            try {
                Map<?, ?> map = (Map<?, ?>) rawPayload;
                LobbiesUpdatePayload payload = new LobbiesUpdatePayload();
                
                // Handle lobbies array with safe conversion
                if (map.containsKey("lobbies") && map.get("lobbies") instanceof List) {
                    List<?> rawLobbies = (List<?>) map.get("lobbies");
                    List<Lobby> lobbies = new ArrayList<>();
                    
                    // Convert each object in the list to a Lobby
                    for (Object item : rawLobbies) {
                        if (item instanceof Lobby) {
                            lobbies.add((Lobby) item);
                        } else if (item instanceof Map) {
                            Lobby lobby = convertMapToLobby((Map<?, ?>) item);
                            if (lobby != null) {
                                lobbies.add(lobby);
                            }
                        }
                    }
                    
                    payload.setLobbies(lobbies);
                }
                
                // Handle event string
                if (map.containsKey("event") && map.get("event") != null) {
                    payload.setEvent(map.get("event").toString());
                }
                
                return payload;
            } catch (Exception e) {
                Log.e(TAG, "Error converting Map to LobbiesUpdatePayload: " + e.getMessage(), e);
                return null;
            }
        }
        
        Log.e(TAG, "Payload is neither LobbiesUpdatePayload nor Map: " + 
              (rawPayload != null ? rawPayload.getClass().getName() : "null"));
        return null;
    }
    
    public void setLobbiesPayload(LobbiesUpdatePayload payload) {
        setPayload(payload);
    }
    
    /**
     * Helper method to convert a Map to a Lobby object
     */
    private Lobby convertMapToLobby(Map<?, ?> map) {
        if (map == null) return null;
        
        Lobby lobby = new Lobby();
        
        if (map.containsKey("_id")) {
            lobby.setLobbyId(map.get("_id").toString());
        }
        
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
        
        if (map.containsKey("isLocked") && map.get("isLocked") instanceof Boolean) {
            lobby.setLocked((Boolean) map.get("isLocked"));
        }
        
        if (map.containsKey("numRounds") && map.get("numRounds") instanceof Number) {
            lobby.setNumRounds(((Number) map.get("numRounds")).intValue());
        }
        
        if (map.containsKey("roundDurationSeconds") && map.get("roundDurationSeconds") instanceof Number) {
            lobby.setRoundDurationSeconds(((Number) map.get("roundDurationSeconds")).intValue());
        }
        
        // Handle players list if present
        if (map.containsKey("players") && map.get("players") instanceof List) {
            List<?> playersList = (List<?>) map.get("players");
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
            lobby.setPlayers(players);
        }
        
        // Handle host user if present
        if (map.containsKey("hostUser") && map.get("hostUser") instanceof Map) {
            User hostUser = convertMapToUser((Map<?, ?>) map.get("hostUser"));
            lobby.setHostUser(hostUser);
        }
        
        return lobby;
    }
    
    /**
     * Helper method to convert a Map to a User object
     */
    private User convertMapToUser(Map<?, ?> map) {
        if (map == null) return null;
        
        User user = new User();
        
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
     * Payload containing lobbies update information
     */
    public static class LobbiesUpdatePayload {
        @Json(name = "lobbies")
        private List<Lobby> lobbies;
        
        @Json(name = "event")
        private String event; // "created", "updated", "deleted"
        
        public LobbiesUpdatePayload() {
        }
        
        public List<Lobby> getLobbies() {
            return lobbies;
        }
        
        public void setLobbies(List<Lobby> lobbies) {
            this.lobbies = lobbies;
        }
        
        public String getEvent() {
            return event;
        }
        
        public void setEvent(String event) {
            this.event = event;
        }
    }
}
