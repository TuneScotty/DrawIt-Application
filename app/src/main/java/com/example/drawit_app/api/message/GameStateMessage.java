package com.example.drawit_app.api.message;

import android.util.Log;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.User;
import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket message for game state updates
 */
public class GameStateMessage extends WebSocketMessage {
    // The server actually sends 'gamePayload' instead of 'payload' for game state messages
    @Json(name = "gamePayload")
    private Object gamePayload;
    private static final String TAG = "GameStateMessage";
    
    // Static flags to avoid excessive logging
    private static boolean payloadFallbackLogged = false;
    private static boolean gamePayloadFieldLogged = false;
    
    public GameStateMessage() {
        super("game_state", null);
    }
    
    /**
     * Safely retrieves the payload as a GamePayload object
     * Handles conversion from Map if necessary to prevent ClassCastException
     * Checks both standard payload and gamePayload fields to handle server inconsistency
     */
    public GamePayload getGamePayload() {
        // Try gamePayload first (sent by server)
        Object rawPayload = this.gamePayload;
        
        // If gamePayload is null, fall back to the standard payload
        if (rawPayload == null) {
            // Only log this once per session with a static flag
            if (!payloadFallbackLogged) {
                Log.d(TAG, "Using standard payload for game state messages");
                payloadFallbackLogged = true;
            }
            rawPayload = getPayload();
        } else {
            if (!gamePayloadFieldLogged) {
                Log.d(TAG, "Using gamePayload field for game state");
                gamePayloadFieldLogged = true;
            }
        }
        
        // Direct cast if already correct type
        if (rawPayload instanceof GamePayload) {
            return (GamePayload) rawPayload;
        }
        
        // Handle case where payload is a Map (from Moshi deserialization)
        if (rawPayload instanceof Map) {
            try {
                Map<?, ?> payloadMap = (Map<?, ?>) rawPayload;
                GamePayload payload = new GamePayload();
                
                // Extract game data
                if (payloadMap.containsKey("game")) {
                    Object gameObj = payloadMap.get("game");
                    if (gameObj instanceof Map) {
                        Game game = convertMapToGame((Map<?, ?>) gameObj);
                        payload.setGame(game);
                        
                        // Set other fields from the game if available
                        if (game != null) {
                            payload.setGameId(game.getGameId());
                            payload.setLobbyId(game.getLobbyId());
                            payload.setStatus(game.getState().toString());
                            payload.setCurrentRound(game.getCurrentRound());
                            payload.setMaxRounds(game.getTotalRounds());
                            payload.setRoundDurationSeconds(game.getRoundDurationSeconds());
                        }
                    } else if (gameObj instanceof Game) {
                        Game game = (Game) gameObj;
                        payload.setGame(game);
                    }
                }
                
                // Extract other fields directly if not set from game
                if (payload.getGameId() == null && payloadMap.containsKey("game_id")) {
                    payload.setGameId(payloadMap.get("game_id").toString());
                }
                
                if (payload.getLobbyId() == null && payloadMap.containsKey("lobby_id")) {
                    payload.setLobbyId(payloadMap.get("lobby_id").toString());
                }
                
                if (payload.getStatus() == null && payloadMap.containsKey("status")) {
                    payload.setStatus(payloadMap.get("status").toString());
                }
                
                if (payloadMap.containsKey("current_round") && payloadMap.get("current_round") instanceof Number) {
                    payload.setCurrentRound(((Number) payloadMap.get("current_round")).intValue());
                }
                
                if (payloadMap.containsKey("max_rounds") && payloadMap.get("max_rounds") instanceof Number) {
                    payload.setMaxRounds(((Number) payloadMap.get("max_rounds")).intValue());
                }
                
                if (payloadMap.containsKey("round_duration_seconds") && payloadMap.get("round_duration_seconds") instanceof Number) {
                    payload.setRoundDurationSeconds(((Number) payloadMap.get("round_duration_seconds")).intValue());
                }
                
                return payload;
            } catch (Exception e) {
                Log.e(TAG, "Error converting Map to GamePayload: " + e.getMessage(), e);
                return null;
            }
        }
        
        // Handle any unexpected payload type or null by returning a safe default
        Log.w(TAG, "Payload is neither GamePayload nor Map: " + 
              (rawPayload != null ? rawPayload.getClass().getName() : "null"));
        return createDefaultGamePayload();
    }
    
    /**
     * Set game payload in both fields for maximum compatibility
     * @param payload The game payload to set
     */
    public void setGamePayload(GamePayload payload) {
        if (payload != null) {
            this.gamePayload = payload;
            setPayload(payload);
        } else {
            Log.w(TAG, "Attempted to set null game payload, creating empty default instead");
            GamePayload emptyPayload = createDefaultGamePayload();
            this.gamePayload = emptyPayload;
            setPayload(emptyPayload);
        }
    }
    
    /**
     * Creates a default GamePayload with safe values to prevent null pointer exceptions
     * @return A non-null GamePayload with default values
     */
    private GamePayload createDefaultGamePayload() {
        GamePayload emptyPayload = new GamePayload();
        Game game = new Game();
        game.setGameId("game_" + System.currentTimeMillis());
        game.setGameState(Game.GameState.WAITING);
        emptyPayload.setGame(game);
        emptyPayload.setEvent("unknown");
        Log.d(TAG, "Created default GamePayload to prevent NPE");
        return emptyPayload;
    }
    
    /**
     * Helper method to convert a Map to a Game object
     */
    private Game convertMapToGame(Map<?, ?> map) {
        if (map == null) return null;
        
        Game game = new Game();
        
        if (map.containsKey("_id")) {
            // Game doesn't have setId, it has setGameId
            game.setGameId(map.get("_id").toString());
        }
        
        if (map.containsKey("gameId")) {
            game.setGameId(map.get("gameId").toString());
        }
        
        if (map.containsKey("lobbyId")) {
            game.setLobbyId(map.get("lobbyId").toString());
        }
        
        if (map.containsKey("status")) {
            try {
                // Convert to uppercase to match Java enum naming convention
                String statusStr = map.get("status").toString().toUpperCase();
                game.setGameState(Game.GameState.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                // Fall back to ACTIVE state if the server sends an unknown state
                Log.w(TAG, "Unknown game state received: " + map.get("status").toString() + ", using ACTIVE");
                game.setGameState(Game.GameState.ACTIVE);
            }
        }
        
        if (map.containsKey("currentRound") && map.get("currentRound") instanceof Number) {
            game.setCurrentRound(((Number) map.get("currentRound")).intValue());
        }
        
        if (map.containsKey("maxRounds") && map.get("maxRounds") instanceof Number) {
            game.setTotalRounds(((Number) map.get("maxRounds")).intValue());
        }
        
        if (map.containsKey("roundDurationSeconds") && map.get("roundDurationSeconds") instanceof Number) {
            game.setRoundDurationSeconds(((Number) map.get("roundDurationSeconds")).intValue());
        }
        
        // Extract currentDrawer if present
        if (map.containsKey("currentDrawer") && map.get("currentDrawer") instanceof Map) {
            User drawer = convertMapToUser((Map<?, ?>) map.get("currentDrawer"));
            if (drawer != null) {
                game.setCurrentDrawer(drawer);
                Log.d(TAG, "Set current drawer: " + drawer.getUsername() + " (" + drawer.getUserId() + ")");
            }
        }
        
        // Extract wordToGuess if present
        if (map.containsKey("wordToGuess")) {
            String word = map.get("wordToGuess").toString();
            game.setCurrentWord(word);
            Log.d(TAG, "Set word to guess: " + word);
        }
        
        // Handle players list if present
        if (map.containsKey("players") && map.get("players") instanceof List) {
            List<?> playersList = (List<?>) map.get("players");
            Map<String, Float> playerScores = new HashMap<>();
            List<User> players = new ArrayList<>();
            
            for (Object playerObj : playersList) {
                if (playerObj instanceof Map) {
                    Map<?, ?> playerMap = (Map<?, ?>) playerObj;
                    if (playerMap.containsKey("userId")) {
                        // Extract player info for scores
                        String userId = playerMap.get("userId").toString();
                        float score = 0.0f;
                        if (playerMap.containsKey("score") && playerMap.get("score") instanceof Number) {
                            score = ((Number) playerMap.get("score")).floatValue();
                        }
                        playerScores.put(userId, score);
                        
                        // Create User object for players list
                        User player = convertMapToUser(playerMap);
                        if (player != null) {
                            players.add(player);
                        }
                    }
                }
            }
            
            if (!playerScores.isEmpty()) {
                game.setPlayerScores(playerScores);
            }
            
            if (!players.isEmpty()) {
                game.setPlayers(players);
                Log.d(TAG, "Set " + players.size() + " players in game");
            }
        }
        
        return game;
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
     * Payload containing game state information
     */
    public static class GamePayload {
        @Json(name = "game")
        private Game game;
        
        @Json(name = "game_id")
        private String gameId;
        
        @Json(name = "lobby_id")
        private String lobbyId;
        
        @Json(name = "status")
        private String status; // "active", "finished", etc.
        
        @Json(name = "current_round")
        private int currentRound;
        
        @Json(name = "max_rounds")
        private int maxRounds;
        
        @Json(name = "round_duration_seconds")
        private int roundDurationSeconds;
        
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
        
        @Json(name = "drawing_paths")
        private String drawingPaths; // JSON string representation of drawing paths
        
        @Json(name = "user_id")
        private String userId; // ID of the user who triggered the event (e.g., submitted a drawing)
        
        @Json(name = "current_drawer")
        private User currentDrawer;
        
        @Json(name = "word_to_guess")
        private String wordToGuess;
        
        public GamePayload() {
        }
        
        public Game getGame() {
            return game;
        }
        
        public void setGame(Game game) {
            this.game = game;
        }
        
        public String getEvent() {
            // If we have an explicit event, return it
            if (event != null) {
                return event;
            }
            
            // If no explicit event but we have a game with ACTIVE state,
            // treat it as a "started" event for compatibility
            if (game != null && game.getGameState() == Game.GameState.ACTIVE) {
                return "started";
            }
            
            // Default case
            return null;
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
        
        public String getDrawingPaths() {
            return drawingPaths;
        }
        
        public void setDrawingPaths(String drawingPaths) {
            this.drawingPaths = drawingPaths;
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

        public String getGameId() {
            return gameId;
        }

        public void setGameId(String gameId) {
            this.gameId = gameId;
        }

        public String getLobbyId() {
            return lobbyId;
        }

        public void setLobbyId(String lobbyId) {
            this.lobbyId = lobbyId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getCurrentRound() {
            return currentRound;
        }

        public void setCurrentRound(int currentRound) {
            this.currentRound = currentRound;
        }

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }

        public int getRoundDurationSeconds() {
            return roundDurationSeconds;
        }

        public void setRoundDurationSeconds(int roundDurationSeconds) {
            this.roundDurationSeconds = roundDurationSeconds;
        }
        
        public User getCurrentDrawer() {
            return currentDrawer;
        }
        
        public void setCurrentDrawer(User currentDrawer) {
            this.currentDrawer = currentDrawer;
        }
        
        public String getWordToGuess() {
            return wordToGuess;
        }
        
        public void setWordToGuess(String wordToGuess) {
            this.wordToGuess = wordToGuess;
        }
        
        /**
         * Get message from the payload
         * @return The message or null if not available
         */
        public String getMessage() {
            // Try to extract message from event or status
            if (event != null && event.contains(":")) {
                return event.split(":", 2)[1].trim();
            }
            return null;
        }
        
        /**
         * Get game state from the payload
         * @return The game state or null if not available
         */
        public Game.GameState getGameState() {
            if (game != null) {
                return game.getGameState();
            }
            return null;
        }
    }
}
