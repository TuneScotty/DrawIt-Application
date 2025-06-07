package com.example.drawit_app.network.message;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.squareup.moshi.Json;

import java.util.List;
import java.util.Map;

/**
 * WebSocket message for game state updates
 */
public class GameStateMessage extends WebSocketMessage {
    
    public GameStateMessage() {
        super("game_state", null);
    }
    
    public GamePayload getGamePayload() {
        return (GamePayload) getPayload();
    }
    
    public void setGamePayload(GamePayload payload) {
        setPayload(payload);
    }
    
    /**
     * Payload containing game state information
     */
    public static class GamePayload {
        @Json(name = "game")
        private Game game;
        
        @Json(name = "event")
        private String event; // "started", "round_started", "drawing_submitted", "voting_started", "vote_submitted", "round_ended", "game_ended"
        
        @Json(name = "round_number")
        private int roundNumber;
        
        @Json(name = "current_word")
        private String currentWord;
        
        @Json(name = "time_remaining_seconds")
        private int timeRemainingSeconds;
        
        @Json(name = "drawings")
        private List<Drawing> drawings;
        
        @Json(name = "player_scores")
        private Map<String, Float> playerScores;
        
        @Json(name = "user_id")
        private String userId; // ID of the user who triggered the event (e.g., submitted a drawing)
        
        public GamePayload() {
        }
        
        public Game getGame() {
            return game;
        }
        
        public void setGame(Game game) {
            this.game = game;
        }
        
        public String getEvent() {
            return event;
        }
        
        public void setEvent(String event) {
            this.event = event;
        }
        
        public int getRoundNumber() {
            return roundNumber;
        }
        
        public void setRoundNumber(int roundNumber) {
            this.roundNumber = roundNumber;
        }
        
        public String getCurrentWord() {
            return currentWord;
        }
        
        public void setCurrentWord(String currentWord) {
            this.currentWord = currentWord;
        }
        
        public int getTimeRemainingSeconds() {
            return timeRemainingSeconds;
        }
        
        public void setTimeRemainingSeconds(int timeRemainingSeconds) {
            this.timeRemainingSeconds = timeRemainingSeconds;
        }
        
        public List<Drawing> getDrawings() {
            return drawings;
        }
        
        public void setDrawings(List<Drawing> drawings) {
            this.drawings = drawings;
        }
        
        public Map<String, Float> getPlayerScores() {
            return playerScores;
        }
        
        public void setPlayerScores(Map<String, Float> playerScores) {
            this.playerScores = playerScores;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
