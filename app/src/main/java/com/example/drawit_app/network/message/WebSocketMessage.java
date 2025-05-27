package com.example.drawit_app.network.message;

import com.squareup.moshi.Json;

/**
 * Base class for all WebSocket messages
 */
public class WebSocketMessage {
    @Json(name = "type")
    private String type;
    
    @Json(name = "payload")
    private Object payload;
    
    public WebSocketMessage() {
    }
    
    public WebSocketMessage(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public void setPayload(Object payload) {
        this.payload = payload;
    }
    
    /**
     * Payload for lobby-related WebSocket messages
     */
    public static class LobbyPayload {
        @Json(name = "lobby_id")
        private String lobbyId;
        
        public LobbyPayload() {
        }
        
        public LobbyPayload(String lobbyId) {
            this.lobbyId = lobbyId;
        }
        
        public String getLobbyId() {
            return lobbyId;
        }
        
        public void setLobbyId(String lobbyId) {
            this.lobbyId = lobbyId;
        }
    }
    
    /**
     * Payload for rating-related WebSocket messages
     */
    public static class RatingPayload {
        @Json(name = "game_id")
        private String gameId;
        
        @Json(name = "drawing_id")
        private String drawingId;
        
        @Json(name = "rating")
        private float rating;
        
        public RatingPayload() {
        }
        
        public RatingPayload(String gameId, String drawingId, float rating) {
            this.gameId = gameId;
            this.drawingId = drawingId;
            this.rating = rating;
        }
        
        public String getGameId() {
            return gameId;
        }
        
        public void setGameId(String gameId) {
            this.gameId = gameId;
        }
        
        public String getDrawingId() {
            return drawingId;
        }
        
        public void setDrawingId(String drawingId) {
            this.drawingId = drawingId;
        }
        
        public float getRating() {
            return rating;
        }
        
        public void setRating(float rating) {
            this.rating = rating;
        }
    }
}
