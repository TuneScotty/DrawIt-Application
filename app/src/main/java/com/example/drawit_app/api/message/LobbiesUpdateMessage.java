package com.example.drawit_app.network.message;

import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * WebSocket message for lobbies update notifications
 */
public class LobbiesUpdateMessage extends WebSocketMessage {
    
    public static final String TYPE_LOBBIES_UPDATE = "lobbies_update";
    
    public LobbiesUpdateMessage() {
        super(TYPE_LOBBIES_UPDATE, null);
    }
    
    // Modified to handle raw Map objects safely
    public LobbiesUpdatePayload getLobbiesPayload() {
        Object rawPayload = getPayload();
        
        // If payload is already the correct type, return it
        if (rawPayload instanceof LobbiesUpdatePayload) {
            return (LobbiesUpdatePayload) rawPayload;
        }
        
        // If payload is a Map (what Moshi often creates), convert it
        if (rawPayload instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) rawPayload;
            LobbiesUpdatePayload payload = new LobbiesUpdatePayload();
            
            // Handle lobbies array
            if (map.containsKey("lobbies") && map.get("lobbies") instanceof List) {
                payload.setLobbies((List<Lobby>) map.get("lobbies"));
            }
            
            // Handle event string
            if (map.containsKey("event") && map.get("event") instanceof String) {
                payload.setEvent((String) map.get("event"));
            }
            
            return payload;
        }
        
        return null;
    }
    
    public void setLobbiesPayload(LobbiesUpdatePayload payload) {
        setPayload(payload);
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
