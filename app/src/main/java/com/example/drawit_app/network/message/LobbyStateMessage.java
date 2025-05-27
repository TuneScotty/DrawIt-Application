package com.example.drawit_app.network.message;

import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.squareup.moshi.Json;

import java.util.List;

/**
 * WebSocket message for lobby state updates
 */
public class LobbyStateMessage extends WebSocketMessage {
    
    public LobbyStateMessage() {
        super("lobby_state", null);
    }
    
    public LobbyPayload getLobbyPayload() {
        return (LobbyPayload) getPayload();
    }
    
    public void setLobbyPayload(LobbyPayload payload) {
        setPayload(payload);
    }
    
    /**
     * Payload containing lobby state information
     */
    public static class LobbyPayload {
        @Json(name = "lobby")
        private Lobby lobby;
        
        @Json(name = "players")
        private List<User> players;
        
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
