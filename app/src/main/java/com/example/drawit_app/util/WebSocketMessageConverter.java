package com.example.drawit_app.util;

import android.util.Log;

import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.api.message.ConnectionStatusMessage;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.api.message.LobbiesUpdateMessage;
import com.example.drawit_app.api.message.LobbyStateMessage;
import com.example.drawit_app.api.message.WebSocketMessage;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WebSocketMessageConverter {
    private static final String TAG = "WebSocketMessageConverter";
    private static final String DEFAULT_MESSAGE_TYPE = "unknown";
    
    private final Moshi moshi;
    
    @Inject
    public WebSocketMessageConverter(Moshi moshi) {
        this.moshi = moshi;
    }
    
    // Extract message type from raw JSON data
    public String extractMessageType(String jsonMessage) {
        try {
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = adapter.fromJson(jsonMessage);
            
            if (messageMap != null && messageMap.containsKey("type")) {
                return messageMap.get("type").toString();
            }
            return DEFAULT_MESSAGE_TYPE;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting message type: " + e.getMessage());
            return DEFAULT_MESSAGE_TYPE;
        }
    }
    
    // Parse a JSON message into a specific WebSocketMessage type
    private <T> T parseMessage(String json, Class<T> type) {
        try {
            JsonAdapter<T> adapter = moshi.adapter(type);
            return adapter.fromJson(json);
        } catch (IOException e) {
            Log.e(TAG, "Error parsing message: " + e.getMessage(), e);
            return null;
        }
    }
    
    // Helper method to extract a Game object from a Map (from raw JSON)
    /**
     * Convert raw JSON game data to a Game object
     * @param jsonData The raw JSON data from the server
     * @return A Game object parsed from the JSON data
     */
    public Game convertGameData(String jsonData) {
        try {
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = adapter.fromJson(jsonData);
            
            if (messageMap != null && messageMap.containsKey("game")) {
                Object gameObj = messageMap.get("game");
                if (gameObj instanceof Map) {
                    return extractGameFromMap((Map<?, ?>) gameObj);
                }
            }
            
            // If we couldn't find a game object, try to parse the entire message as a game
            return extractGameFromMap(messageMap);
        } catch (Exception e) {
            Log.e(TAG, "Error converting game data: " + e.getMessage(), e);
            return null;
        }
    }
    
    private Game extractGameFromMap(Map<?, ?> gameMap) {
        if (gameMap == null) return null;
        
        Game game = new Game();
        
        // Extract basic game properties
        if (gameMap.containsKey("gameId")) {
            game.setGameId(gameMap.get("gameId").toString());
        }
        
        if (gameMap.containsKey("lobbyId")) {
            game.setLobbyId(gameMap.get("lobbyId").toString());
        }
        
        if (gameMap.containsKey("numRounds")) {
            try {
                game.setNumRounds(((Number) gameMap.get("numRounds")).intValue());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing numRounds: " + e.getMessage());
                game.setNumRounds(3); // Default to 3 rounds
            }
        } else {
            game.setNumRounds(3); // Default to 3 rounds
        }
        
        if (gameMap.containsKey("roundDurationSeconds")) {
            try {
                game.setRoundDurationSeconds(((Number) gameMap.get("roundDurationSeconds")).intValue());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing roundDurationSeconds: " + e.getMessage());
                game.setRoundDurationSeconds(60); // Default to 60 seconds
            }
        } else {
            game.setRoundDurationSeconds(60); // Default to 60 seconds
        }
        
        // Extract current round
        if (gameMap.containsKey("currentRound")) {
            try {
                game.setCurrentRound(((Number) gameMap.get("currentRound")).intValue());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing currentRound: " + e.getMessage());
                game.setCurrentRound(1); // Default to round 1
            }
        } else {
            game.setCurrentRound(1); // Default to round 1
        }
        
        // Extract total rounds
        if (gameMap.containsKey("totalRounds")) {
            try {
                game.setTotalRounds(((Number) gameMap.get("totalRounds")).intValue());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing totalRounds: " + e.getMessage());
                game.setTotalRounds(3); // Default to 3 rounds
            }
        } else {
            game.setTotalRounds(3); // Default to 3 rounds
        }
        
        // Extract current word
        if (gameMap.containsKey("currentWord")) {
            game.setCurrentWord(gameMap.get("currentWord").toString());
        } else {
            Log.w(TAG, "⚠️ No current word in game data from server");
        }
        
        if (gameMap.containsKey("status") && "active".equals(gameMap.get("status"))) {
            game.setGameState(Game.GameState.ACTIVE);
        } else {
            game.setGameState(Game.GameState.ACTIVE);
        }
        
        // Extract players
        if (gameMap.containsKey("players") && gameMap.get("players") instanceof List) {
            List<?> playersList = (List<?>) gameMap.get("players");
            List<User> players = new ArrayList<>();
            Map<String, Float> playerScores = new HashMap<>();
            
            for (Object playerObj : playersList) {
                User player = convertToUser(playerObj);
                if (player != null) {
                    players.add(player);
                    // Initialize player score to 0
                    playerScores.put(player.getUserId(), 0.0f);
                }
            }
            
            if (!players.isEmpty()) {
                Log.d(TAG, "\u2705 Extracted " + players.size() + " players from game object");
                game.setPlayers(players);
                
                // Process player scores if available in array format
                if (gameMap.containsKey("playerScores")) {
                    Object scoresObj = gameMap.get("playerScores");
                    
                    // Handle array format (from the actual API)
                    if (scoresObj instanceof List) {
                        List<?> scoresList = (List<?>) scoresObj;
                        for (Object scoreObj : scoresList) {
                            if (scoreObj instanceof Map) {
                                Map<?, ?> scoreMap = (Map<?, ?>) scoreObj;
                                String userId = null;
                                Float score = 0.0f;
                                
                                // Extract userId
                                if (scoreMap.containsKey("userId")) {
                                    userId = scoreMap.get("userId").toString();
                                }
                                
                                // Extract score
                                if (scoreMap.containsKey("score") && scoreMap.get("score") instanceof Number) {
                                    score = ((Number) scoreMap.get("score")).floatValue();
                                }
                                
                                if (userId != null) {
                                    playerScores.put(userId, score);
                                    Log.d(TAG, "\ud83d\udcb0 Set player score: " + userId + " = " + score);
                                }
                            }
                        }
                    }
                    // Handle map format (alternative format)
                    else if (scoresObj instanceof Map) {
                        Map<?, ?> scoresMap = (Map<?, ?>) scoresObj;
                        for (Object key : scoresMap.keySet()) {
                            String userId = key.toString();
                            Object value = scoresMap.get(key);
                            
                            if (value instanceof Number) {
                                playerScores.put(userId, ((Number) value).floatValue());
                                Log.d(TAG, "\ud83d\udcb0 Set player score from map: " + userId + " = " + ((Number) value).floatValue());
                            }
                        }
                    }
                }
                
                game.setPlayerScores(playerScores);
            }
        }
        
        // Extract current drawer with improved handling for all formats
        boolean drawerAssigned = false;
        
        // First try to get drawer from currentDrawerId field
        if (gameMap.containsKey("currentDrawerId")) {
            String drawerId = gameMap.get("currentDrawerId").toString();
            game.setCurrentDrawerId(drawerId);
            Log.d(TAG, "🎮 Found currentDrawerId: " + drawerId);
            
            // Find the drawer in the players list
            List<User> players = game.getPlayers();
            if (players != null) {
                for (User player : players) {
                    if (player.getUserId() != null && player.getUserId().equals(drawerId)) {
                        game.setCurrentDrawer(player);
                        Log.d(TAG, "✅ Set current drawer from ID: " + player.getUsername());
                        drawerAssigned = true;
                        break;
                    }
                }
            }
        }
        
        // Then try to get drawer from currentDrawer field if not already assigned
        if (!drawerAssigned && gameMap.containsKey("currentDrawer")) {
            Object drawerObj = gameMap.get("currentDrawer");
            
            // Handle case where currentDrawer is a Map (user object)
            if (drawerObj instanceof Map) {
                Map<?, ?> drawerMap = (Map<?, ?>) drawerObj;
                User drawer = convertToUser(drawerMap);
                if (drawer != null && drawer.getUserId() != null) {
                    game.setCurrentDrawer(drawer);
                    game.setCurrentDrawerId(drawer.getUserId());
                    Log.d(TAG, "✅ Set current drawer from map: " + drawer.getUsername());
                    drawerAssigned = true;
                }
            } 
            // Handle case where currentDrawer is a String (user ID)
            else if (drawerObj instanceof String) {
                String drawerId = (String) drawerObj;
                game.setCurrentDrawerId(drawerId);
                Log.d(TAG, "✅ Set current drawer ID from string: " + drawerId);
                
                // Find the drawer in the players list
                List<User> players = game.getPlayers();
                if (players != null && !players.isEmpty()) {
                    for (User player : players) {
                        if (player.getUserId() != null && player.getUserId().equals(drawerId)) {
                            game.setCurrentDrawer(player);
                            Log.d(TAG, "✅ Found drawer in players list: " + player.getUsername());
                            drawerAssigned = true;
                            break;
                        }
                    }
                }
            }
        }
        
        // If drawer still not assigned, try to find it from the round data
        if (!drawerAssigned && gameMap.containsKey("rounds")) {
            try {
                Object roundsObj = gameMap.get("rounds");
                if (roundsObj instanceof List) {
                    List<?> rounds = (List<?>) roundsObj;
                    if (!rounds.isEmpty() && rounds.get(0) instanceof Map) {
                        Map<?, ?> currentRound = (Map<?, ?>) rounds.get(0);
                        if (currentRound.containsKey("drawerId")) {
                            String drawerId = currentRound.get("drawerId").toString();
                            game.setCurrentDrawerId(drawerId);
                            Log.d(TAG, "✅ Set current drawer ID from rounds data: " + drawerId);
                            
                            // Find the drawer in the players list
                            List<User> players = game.getPlayers();
                            if (players != null && !players.isEmpty()) {
                                for (User player : players) {
                                    if (player.getUserId() != null && player.getUserId().equals(drawerId)) {
                                        game.setCurrentDrawer(player);
                                        Log.d(TAG, "✅ Found drawer from rounds data: " + player.getUsername());
                                        drawerAssigned = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting drawer from rounds data: " + e.getMessage());
            }
        }
        
        // If still no drawer assigned, default to first player if available
        if (!drawerAssigned) {
            List<User> players = game.getPlayers();
            if (players != null && !players.isEmpty()) {
                User firstPlayer = players.get(0);
                if (firstPlayer.getUserId() != null) {
                    game.setCurrentDrawer(firstPlayer);
                    game.setCurrentDrawerId(firstPlayer.getUserId());
                    Log.w(TAG, "⚠️ No drawer found in game data, defaulting to first player: " + firstPlayer.getUsername());
                    drawerAssigned = true;
                }
            }
        }
        
        // Final check - if we still don't have a drawer, log an error
        if (!drawerAssigned) {
            Log.e(TAG, "❌ Failed to assign a drawer for this game state!");
        } else {
            Log.d(TAG, "✅ Final drawer assignment: " + 
                  (game.getCurrentDrawer() != null ? game.getCurrentDrawer().getUsername() : "null") + 
                  ", ID: " + game.getCurrentDrawerId());
        }
        
        return game;
    }
    
    // Parse a lobbies update message with enhanced error handling
    public LobbiesUpdateMessage parseLobbiesUpdateMessage(String json) {
        try {
            // First try with direct adapter
            LobbiesUpdateMessage message = parseMessage(json, LobbiesUpdateMessage.class);
            if (message != null) {
                return message;
            }
            
            // If direct parsing fails, manually parse and construct the message
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> mapAdapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = mapAdapter.fromJson(json);
            
            if (messageMap != null && messageMap.containsKey("type") && "lobbies_update".equals(messageMap.get("type"))) {
                // Create message manually
                LobbiesUpdateMessage lobbiesUpdateMessage = new LobbiesUpdateMessage();
                lobbiesUpdateMessage.setType("lobbies_update");
                
                // Get the payload
                if (messageMap.containsKey("payload") && messageMap.get("payload") instanceof Map) {
                    Map<String, Object> payloadMap = (Map<String, Object>) messageMap.get("payload");
                    LobbiesUpdateMessage.LobbiesUpdatePayload payload = new LobbiesUpdateMessage.LobbiesUpdatePayload();
                    
                    // Extract lobbies list
                    if (payloadMap.containsKey("lobbies") && payloadMap.get("lobbies") instanceof List) {
                        List<?> lobbiesList = (List<?>) payloadMap.get("lobbies");
                        List<Lobby> lobbies = new ArrayList<>();
                        
                        for (Object lobbyObj : lobbiesList) {
                            Lobby lobby = convertToLobby(lobbyObj);
                            if (lobby != null) {
                                lobbies.add(lobby);
                            }
                        }
                        payload.setLobbies(lobbies);
                    }
                    
                    // Set the payload
                    lobbiesUpdateMessage.setPayload(payload);
                    Log.d(TAG, "Successfully manually parsed lobbies_update message");
                    return lobbiesUpdateMessage;
                }
            }
            
            Log.e(TAG, "Could not parse lobbies_update message even with manual parsing");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing lobbies_update message: " + e.getMessage(), e);
            return null;
        }
    }
    
    // Parse a lobby state message from JSON with enhanced error handling
    public LobbyStateMessage parseLobbyStateMessage(String json) {
        try {
            if (json == null || json.isEmpty()) {
                Log.e(TAG, "Empty or null JSON passed to parseLobbyStateMessage");
                return createEmptyLobbyStateMessage();
            }
            
            // Log the message for debugging purposes
            Log.d(TAG, "Parsing lobby_state message: " + json);
            
            // First try with direct adapter
            LobbyStateMessage message = parseMessage(json, LobbyStateMessage.class);
            if (message != null) {
                // Validate that the payload is not null
                if (message.getLobbyPayload() == null) {
                    Log.e(TAG, "Direct parsing resulted in null LobbyPayload, attempting manual parsing");
                } else {
                    Log.d(TAG, "Successfully parsed lobby_state message with direct adapter");
                    return message;
                }
            }
            
            // If direct parsing fails, manually parse and construct the message
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> mapAdapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = mapAdapter.fromJson(json);
            
            if (messageMap != null && messageMap.containsKey("type") && "lobby_state".equals(messageMap.get("type"))) {
                // Create message manually
                LobbyStateMessage lobbyStateMessage = new LobbyStateMessage();
                lobbyStateMessage.setType("lobby_state");
                
                // Get the payload
                Object rawPayload = messageMap.get("payload");
                
                // Handle null payload case
                if (rawPayload == null) {
                    Log.w(TAG, "LobbyStateMessage payload is null, creating empty payload");
                    // Create an empty payload to avoid null pointer exceptions
                    LobbyStateMessage.LobbyPayload emptyPayload = new LobbyStateMessage.LobbyPayload();
                    // Initialize with empty lists to avoid NPEs
                    emptyPayload.setPlayers(new ArrayList<>());
                    lobbyStateMessage.setLobbyPayload(emptyPayload);
                    return lobbyStateMessage;
                }
                
                // Handle Map payload case
                if (rawPayload instanceof Map) {
                    Map<String, Object> payloadMap = (Map<String, Object>) rawPayload;
                    LobbyStateMessage.LobbyPayload payload = new LobbyStateMessage.LobbyPayload();
                    
                    // Extract lobby data
                    if (payloadMap.containsKey("lobby") && payloadMap.get("lobby") instanceof Map) {
                        Map<String, Object> lobbyMap = (Map<String, Object>) payloadMap.get("lobby");
                        Lobby lobby = convertToLobby(lobbyMap);
                        payload.setLobby(lobby);
                    } else {
                        // Create empty lobby if not present to avoid NPEs
                        payload.setLobby(new Lobby());
                    }
                    
                    // Extract host user
                    if (payloadMap.containsKey("hostUser") && payloadMap.get("hostUser") instanceof Map) {
                        Map<String, Object> hostUserMap = (Map<String, Object>) payloadMap.get("hostUser");
                        User hostUser = convertToUser(hostUserMap);
                        payload.setHostUser(hostUser);
                    }
                    
                    // Extract players list
                    if (payloadMap.containsKey("players") && payloadMap.get("players") instanceof List) {
                        List<?> playersList = (List<?>) payloadMap.get("players");
                        List<User> players = new ArrayList<>();
                        
                        for (Object playerObj : playersList) {
                            User player = convertToUser(playerObj);
                            if (player != null) {
                                players.add(player);
                            }
                        }
                        payload.setPlayers(players);
                    } else {
                        // Initialize with empty list to avoid NPEs
                        payload.setPlayers(new ArrayList<>());
                    }
                    
                    // Extract event and user_id
                    if (payloadMap.containsKey("event")) {
                        payload.setEvent(payloadMap.get("event").toString());
                    }
                    
                    if (payloadMap.containsKey("user_id")) {
                        payload.setUserId(payloadMap.get("user_id").toString());
                    }
                    
                    // Set the payload
                    lobbyStateMessage.setLobbyPayload(payload);
                    Log.d(TAG, "Successfully manually parsed lobby_state message");
                    return lobbyStateMessage;
                } else {
                    // Handle case where payload is not a Map
                    Log.w(TAG, "LobbyStateMessage payload is not a Map: " + rawPayload.getClass().getName());
                    LobbyStateMessage.LobbyPayload emptyPayload = new LobbyStateMessage.LobbyPayload();
                    emptyPayload.setPlayers(new ArrayList<>());
                    lobbyStateMessage.setLobbyPayload(emptyPayload);
                    return lobbyStateMessage;
                }
            }
            
            Log.e(TAG, "Could not parse lobby_state message even with manual parsing");
            return createEmptyLobbyStateMessage();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing lobby_state message: " + e.getMessage(), e);
            return createEmptyLobbyStateMessage();
        }
    }
    
    /**
     * Creates an empty but valid LobbyStateMessage to prevent null pointer exceptions
     */
    private LobbyStateMessage createEmptyLobbyStateMessage() {
        LobbyStateMessage message = new LobbyStateMessage();
        message.setType("lobby_state");
        LobbyStateMessage.LobbyPayload payload = new LobbyStateMessage.LobbyPayload();
        payload.setLobby(new Lobby());
        payload.setPlayers(new ArrayList<>());
        payload.setEvent("unknown");
        message.setLobbyPayload(payload);
        Log.d(TAG, "Created empty fallback LobbyStateMessage to prevent NPE");
        return message;
    }
    
    // Parse a game state message with enhanced validation and manual parsing
    public GameStateMessage parseGameStateMessage(String json) {
        try {
            // Log the message for debugging
            Log.d(TAG, "\u2705 Parsing game_state message: " + json);
            
            GameStateMessage message = parseMessage(json, GameStateMessage.class);
            
            if (message != null) {
                // Check if gamePayload is null and create an empty one if needed
                if (message.getGamePayload() == null) {
                    Log.e(TAG, "Direct parsing resulted in null GamePayload, attempting to parse raw JSON");
                    
                    // Try to parse the raw JSON structure
                    Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
                    JsonAdapter<Map<String, Object>> mapAdapter = moshi.adapter(mapType);
                    Map<String, Object> messageMap = mapAdapter.fromJson(json);
                    
                    // Extract gamePayload directly from server's format
                    if (messageMap != null && messageMap.containsKey("gamePayload")) {
                        Log.d(TAG, "Found gamePayload in raw JSON");
                        
                        // Create the payload structure
                        GameStateMessage.GamePayload payload = new GameStateMessage.GamePayload();
                        payload.setEvent("started"); // Default event
                        
                        // Extract game data
                        Map<?, ?> payloadMap = (Map<?, ?>) messageMap.get("gamePayload");
                        if (payloadMap != null && payloadMap.containsKey("game")) {
                            Map<?, ?> gameMap = (Map<?, ?>) payloadMap.get("game");
                            Game game = extractGameFromMap(gameMap);
                            payload.setGame(game);
                            Log.d(TAG, "\u2705 Successfully extracted game from gamePayload");
                        }
                        
                        message.setGamePayload(payload);
                    }
                    else if (messageMap != null && messageMap.containsKey("gameId")) {
                        // Fallback to old parsing method if needed
                        GameStateMessage.GamePayload emptyPayload = new GameStateMessage.GamePayload();
                        emptyPayload.setEvent("started");
                        Game game = new Game();
                        game.setGameId(messageMap.get("gameId").toString());
                        game.setGameState(Game.GameState.ACTIVE);
                        emptyPayload.setGame(game);
                        message.setGamePayload(emptyPayload);
                    }
                }
                
                // Validate and enhance the game state message
                if (message.getGamePayload() != null && message.getGamePayload().getGame() != null) {
                    message.getGamePayload();
                    if (!message.getGamePayload().getGame().getGameId().isEmpty()) {

                        Game game = message.getGamePayload().getGame();

                        // Always ensure game state is set
                        if (game.getGameState() == null) {
                            game.setGameState(Game.GameState.ACTIVE);
                            Log.d(TAG, "Set default game state to ACTIVE");
                        }

                        // CRITICAL FIX: Ensure current drawer is set
                        if (game.getCurrentDrawer() == null) {
                            Log.w(TAG, "⚠️ Game has no current drawer set! Attempting to assign one...");

                            // Try to get drawer from players list
                            List<User> players = game.getPlayers();
                            if (players != null && !players.isEmpty()) {
                                // Select first player as drawer if none is set
                                User firstPlayer = players.get(0);
                                game.setCurrentDrawer(firstPlayer);
                                game.setCurrentDrawerId(firstPlayer.getUserId());
                                Log.d(TAG, "✅ Set first player as drawer: " + firstPlayer.getUsername() +
                                        " (ID: " + firstPlayer.getUserId() + ")");
                            } else {
                                Log.e(TAG, "❌ Cannot set drawer - no players available in game object");
                            }
                        } else {
                            // Make sure currentDrawerId is also set
                            if (game.getCurrentDrawerId() == null && game.getCurrentDrawer() != null) {
                                game.setCurrentDrawerId(game.getCurrentDrawer().getUserId());
                                Log.d(TAG, "Set currentDrawerId from currentDrawer: " + game.getCurrentDrawerId());
                            }
                        }

                        // Check if server provided a word
                        if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
                            Log.w(TAG, "⚠️ Warning: Server did not provide a word for this game state");
                        }

                        // Initialize player scores if missing
                        if (game.getPlayerScores() == null || game.getPlayerScores().isEmpty()) {
                            Log.d(TAG, "Initializing player scores map in game payload");
                            Map<String, Float> playerScores = new HashMap<>();

                            // Initialize scores for all players if available
                            List<User> players = game.getPlayers();
                            if (players != null) {
                                for (User player : players) {
                                    playerScores.put(player.getUserId(), 0.0f);
                                }
                                Log.d(TAG, "Initialized scores for " + players.size() + " players");
                            }

                            game.setPlayerScores(playerScores);
                        }

                        // Make sure current round is set
                        if (game.getCurrentRound() <= 0) {
                            game.setCurrentRound(1);
                            Log.d(TAG, "Set current round to 1");
                        }

                        // Make sure total rounds is set
                        if (game.getTotalRounds() <= 0) {
                            game.setTotalRounds(3); // Default to 3 rounds
                            Log.d(TAG, "Set total rounds to default (3)");
                        }

                        Log.d(TAG, "✅ Enhanced game state message: " + game.getGameId() +
                                ", Drawer: " + (game.getCurrentDrawer() != null ?
                                game.getCurrentDrawer().getUsername() : "null") +
                                ", Word: " + game.getCurrentWord() +
                                ", Round: " + game.getCurrentRound() + "/" + game.getTotalRounds());

                        return message;
                    }
                }
            }
            
            Log.e(TAG, "Could not parse game_state message even with manual parsing");
            return message; // Return original message or null
        } catch (Exception e) {
            Log.e(TAG, "Error parsing game_state message: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a GameStateMessage based on a WebSocket start_game message
     * This supports the new WebSocket-only game management approach
     * 
     * @param json The JSON string containing the start_game message
     * @return A properly formed GameStateMessage object or null if parsing fails
     */
    public GameStateMessage createGameStateFromStartGameMessage(String json) {
        try {
            // Log the full JSON for debugging purposes
            Log.d(TAG, "Creating game state from start_game message: " + json);

            try {
                GameStateMessage message = parseMessage(json, GameStateMessage.class);
                if (message != null && message.getGamePayload() != null && 
                    message.getGamePayload().getGame() != null) {
                    Log.i(TAG, "Successfully parsed start_game with same structure as game_state");
                    // Ensure event is set to started
                    message.getGamePayload().setEvent("started");
                    return message;
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not parse directly as GameStateMessage, trying alternative approach");
            }
            
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = adapter.fromJson(json);
            
            if (messageMap != null && "start_game".equals(messageMap.get("type"))) {
                String gameId;
                if (messageMap.containsKey("gameId")) {
                    gameId = messageMap.get("gameId").toString();
                } else if (messageMap.containsKey("game_id")) {
                    gameId = messageMap.get("game_id").toString();
                } else if (messageMap.containsKey("temp_game_id")) {
                    gameId = messageMap.get("temp_game_id").toString();
                } else {
                    gameId = "game_" + System.currentTimeMillis();
                }
                
                // Extract lobby ID
                String lobbyId = messageMap.containsKey("lobby_id") ?
                               messageMap.get("lobby_id").toString() : 
                               (messageMap.containsKey("lobbyId") ?
                               messageMap.get("lobbyId").toString() : "");
                               
                int numRounds = messageMap.containsKey("num_rounds") && messageMap.get("num_rounds") instanceof Number ?
                              ((Number) messageMap.get("num_rounds")).intValue() : 
                              3;
                              
                int roundDuration = messageMap.containsKey("round_duration") && messageMap.get("round_duration") instanceof Number ?
                                 ((Number) messageMap.get("round_duration")).intValue() : 
                                 60;
                
                // Create a game state message from the game start event
                GameStateMessage gameStateMessage = new GameStateMessage();
                gameStateMessage.setType("game_state"); // Convert to game_state type for compatibility
                
                // Set the necessary fields for game state
                GameStateMessage.GamePayload payload = new GameStateMessage.GamePayload();
                payload.setEvent("started");
                
                // Set up the game object inside the payload
                Game game = new Game();
                game.setGameId(gameId);
                game.setLobbyId(lobbyId);
                game.setNumRounds(numRounds);
                game.setRoundDurationSeconds(roundDuration);
                game.setGameState(Game.GameState.ACTIVE);
                
                // SERVER FORMAT: Extract players array directly and initialize player scores
                if (messageMap.containsKey("gamePayload") && messageMap.get("gamePayload") instanceof Map) {
                    Map<?, ?> gamePayload = (Map<?, ?>) messageMap.get("gamePayload");
                    if (gamePayload.containsKey("game") && gamePayload.get("game") instanceof Map) {
                        Map<?, ?> gameObj = (Map<?, ?>) gamePayload.get("game");
                        if (gameObj.containsKey("players") && gameObj.get("players") instanceof List) {
                            List<?> playersList = (List<?>) gameObj.get("players");
                            List<User> players = new ArrayList<>();
                            Map<String, Float> playerScores = new HashMap<>();
                            
                            for (Object playerObj : playersList) {
                                User player = convertToUser(playerObj);
                                if (player != null) {
                                    players.add(player);
                                    playerScores.put(player.getUserId(), 0.0f);
                                }
                            }
                            
                            if (!players.isEmpty()) {
                                Log.d(TAG, "\u2705 Found " + players.size() + " players in gamePayload.game.players");
                                game.setPlayerScores(playerScores);
                                // We can't store the players list directly in payload as there's no setExtraData method
                                // But the server will handle this through the game state
                            }
                        }
                    }
                }
                
                // Add the game to the payload
                payload.setGame(game);
                    
                // Set other payload properties directly
                payload.setRoundNumber(1);
                
                // Set payload using both methods for maximum compatibility
                gameStateMessage.setGamePayload(payload);
                
                // Add players list from original message if available
                // This is critical for non-host clients to know who's in the game
                if (messageMap.containsKey("players") && messageMap.get("players") instanceof List) {
                    List<?> playersList = (List<?>) messageMap.get("players");
                    List<User> players = new ArrayList<>();

                    assert playersList != null;
                    for (Object playerObj : playersList) {
                        User player = convertToUser(playerObj);
                        if (player != null) {
                            players.add(player);
                        }
                    }
                    
                    if (!players.isEmpty()) {
                        Log.d(TAG, "Adding " + players.size() + " players from start_game message");

                        Map<String, Float> playerScores = new HashMap<>();
                        for (User player : players) {
                            playerScores.put(player.getUserId(), 0.0f);
                        }
                        payload.setPlayerScores(playerScores);

                        game.setPlayerScores(playerScores);
                    }
                }

                if (payload.getTimeRemainingSeconds() <= 0) {
                    payload.setTimeRemainingSeconds(game.getRoundDurationSeconds());
                }

                if (payload.getRoundNumber() <= 0) {
                    payload.setRoundNumber(1);
                }
                
                Log.i(TAG, "✅ Created GameStateMessage for game start: " + gameId + ", lobby: " + lobbyId);
                return gameStateMessage;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error creating game state from start_game message: " + e.getMessage(), e);
            return null;
        }
    }

    public ConnectionStatusMessage parseConnectionStatusMessage(String json) {
        return parseMessage(json, ConnectionStatusMessage.class);
    }

    /**
     * Parse a chat message from JSON
     * @param json The JSON string to parse
     * @return ChatMessage object or null if parsing fails
     */
    public ChatMessage parseChatMessage(String json) {
        try {
            // Log the message for debugging
            Log.d(TAG, "Parsing chat_message: " + json);
            
            // Parse the raw JSON structure
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> mapAdapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = mapAdapter.fromJson(json);
            
            if (messageMap != null && messageMap.containsKey("type") && "chat_message".equals(messageMap.get("type"))) {
                ChatMessage chatMessage = new ChatMessage();
                
                // Extract message content
                if (messageMap.containsKey("message")) {
                    chatMessage.setMessage(messageMap.get("message").toString());
                }
                
                // Extract game ID
                if (messageMap.containsKey("game_id")) {
                    chatMessage.setGameId(messageMap.get("game_id").toString());
                }
                
                // Extract sender info
                if (messageMap.containsKey("sender") && messageMap.get("sender") instanceof Map) {
                    Map<?, ?> senderMap = (Map<?, ?>) messageMap.get("sender");
                    User sender = convertToUser(senderMap);
                    chatMessage.setSender(sender);
                }
                
                // Extract timestamp
                if (messageMap.containsKey("timestamp")) {
                    try {
                        Object timestampObj = messageMap.get("timestamp");
                        if (timestampObj instanceof Number) {
                            chatMessage.setTimestamp(((Number) timestampObj).longValue());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing timestamp: " + e.getMessage());
                        chatMessage.setTimestamp(System.currentTimeMillis());
                    }
                } else {
                    chatMessage.setTimestamp(System.currentTimeMillis());
                }
                
                // Set default message type
                chatMessage.setType(ChatMessage.MessageType.PLAYER_MESSAGE);
                
                Log.d(TAG, "Successfully parsed chat message from: " + 
                      (chatMessage.getSender() != null ? chatMessage.getSender().getUsername() : "unknown"));
                
                return chatMessage;
            }
            
            Log.e(TAG, "Could not parse chat_message");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing chat_message: " + e.getMessage(), e);
            return null;
        }
    }

    public Lobby convertToLobby(Object rawObject) {
        if (rawObject instanceof Lobby) {
            return (Lobby) rawObject;
        }
        
        if (rawObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawObject;
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
            
            if (map.containsKey("players") && map.get("players") instanceof List) {
                List<?> rawPlayers = (List<?>) map.get("players");
                List<User> players = new ArrayList<>();

                assert rawPlayers != null;
                for (Object rawPlayer : rawPlayers) {
                    User user = convertToUser(rawPlayer);
                    if (user != null) {
                        players.add(user);
                    }
                }
                lobby.setPlayers(players);
            }
            
            return lobby;
        }
        return null;
    }
    
    // Convert a raw object to a User instance
    public User convertToUser(Object rawObject) {
        if (rawObject instanceof User) {
            return (User) rawObject;
        }
        
        if (rawObject instanceof Map) {
            Map<?, ?> playerMap = (Map<?, ?>) rawObject;
            User user = new User();
            
            if (playerMap.containsKey("userId")) {
                user.setUserId(playerMap.get("userId").toString());
            }
            
            if (playerMap.containsKey("username")) {
                user.setUsername(playerMap.get("username").toString());
            }
            
            if (playerMap.containsKey("avatar")) {
                user.setAvatarUrl(playerMap.get("avatar").toString());
            }
            
            return user;
        }
        return null;
    }
    
    // Convert a raw object to a Game instance
    public Game convertToGame(Object rawObject) {
        if (rawObject instanceof Game) {
            return (Game) rawObject;
        }
        
        if (rawObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawObject;
            Game game = new Game();
            
            if (map.containsKey("gameId")) {
                game.setGameId(map.get("gameId").toString());
            }
            
            if (map.containsKey("lobbyId")) {
                game.setLobbyId(map.get("lobbyId").toString());
            }
            
            if (map.containsKey("gameState") || map.containsKey("status")) {
                String stateStr = map.containsKey("gameState") ? 
                    map.get("gameState").toString() : map.get("status").toString();
                
                try {
                    // First try direct conversion to uppercase
                    Game.GameState gameState = Game.GameState.valueOf(stateStr.toUpperCase());
                    game.setGameState(gameState);
                } catch (IllegalArgumentException e) {
                    // Handle specific server states that don't directly map to our enum
                    switch (stateStr.toLowerCase()) {
                        case "waiting":
                            game.setGameState(Game.GameState.WAITING);
                            break;
                        case "active":
                            game.setGameState(Game.GameState.ACTIVE);
                            break;
                        case "finished":
                            game.setGameState(Game.GameState.FINISHED);
                            break;
                        default:
                            // Default to ACTIVE for any other unknown state
                            Log.w(TAG, "Unknown game state received: " + stateStr + ", falling back to ACTIVE");
                            game.setGameState(Game.GameState.ACTIVE);
                            break;
                    }
                }
            } else {
                // If no state specified, default to WAITING
                Log.d(TAG, "No game state specified in game data, defaulting to WAITING");
                game.setGameState(Game.GameState.WAITING);
            }
            
            if (map.containsKey("currentRound") && map.get("currentRound") instanceof Number) {
                game.setCurrentRound(((Number) map.get("currentRound")).intValue());
            }
            
            if (map.containsKey("currentWord")) {
                game.setCurrentWord(map.get("currentWord").toString());
            }
            
            if (map.containsKey("currentDrawerId")) {
                game.setCurrentDrawerId(map.get("currentDrawerId").toString());
            }
            
            if (map.containsKey("totalRounds") && map.get("totalRounds") instanceof Number) {
                game.setTotalRounds(((Number) map.get("totalRounds")).intValue());
            }
            
            if (map.containsKey("roundDurationSeconds") && map.get("roundDurationSeconds") instanceof Number) {
                game.setRoundDurationSeconds(((Number) map.get("roundDurationSeconds")).intValue());
            }
            
            return game;
        }
        return null;
    }
    
    // Convert a raw object to a Drawing instance
    public Drawing convertToDrawing(Object rawObject) {
        if (rawObject instanceof Drawing) {
            return (Drawing) rawObject;
        }
        
        if (rawObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawObject;
            Drawing drawing = new Drawing();
            
            if (map.containsKey("drawingId")) {
                drawing.setDrawingId(map.get("drawingId").toString());
            }
            
            if (map.containsKey("userId")) {
                drawing.setUserId(map.get("userId").toString());
            }
            
            if (map.containsKey("roundId")) {
                drawing.setRoundNumber((int) map.get("roundId"));
            }
            
            if (map.containsKey("paths")) {
                if (map.get("paths") instanceof List) {
                    List<?> rawPaths = (List<?>) map.get("paths");
                    List<Drawing.DrawingPath> drawingPaths = new ArrayList<>();
                    
                    for (Object rawPath : rawPaths) {
                        if (rawPath instanceof Map) {
                            Map<?, ?> pathMap = (Map<?, ?>) rawPath;
                            Drawing.DrawingPath path = new Drawing.DrawingPath();
                            
                            if (pathMap.containsKey("color") && pathMap.get("color") instanceof String) {
                                try {
                                    String colorStr = pathMap.get("color").toString();
                                    if (colorStr.startsWith("#")) {
                                        path.setColor(android.graphics.Color.parseColor(colorStr));
                                    } else {
                                        path.setColor(Integer.parseInt(colorStr));
                                    }
                                } catch (Exception e) {
                                    path.setColor(android.graphics.Color.BLACK);
                                }
                            }
                            
                            if (pathMap.containsKey("width") && pathMap.get("width") instanceof Number) {
                                path.setStrokeWidth(((Number) pathMap.get("width")).floatValue());
                            }
                            
                            if (pathMap.containsKey("points") && pathMap.get("points") instanceof List) {
                                List<?> pointsList = (List<?>) pathMap.get("points");
                                List<Drawing.PointF> points = new ArrayList<>();

                                assert pointsList != null;
                                for (Object pointObj : pointsList) {
                                    if (pointObj instanceof Map) {
                                        Map<?, ?> pointMap = (Map<?, ?>) pointObj;
                                        float x = 0, y = 0;
                                        
                                        if (pointMap.containsKey("x") && pointMap.get("x") instanceof Number) {
                                            x = ((Number) pointMap.get("x")).floatValue();
                                        }
                                        
                                        if (pointMap.containsKey("y") && pointMap.get("y") instanceof Number) {
                                            y = ((Number) pointMap.get("y")).floatValue();
                                        }
                                        
                                        points.add(new Drawing.PointF(x, y));
                                    }
                                }
                                path.setPoints(points);
                            }
                            drawingPaths.add(path);
                        }
                    }
                    drawing.setPaths(drawingPaths);
                } else if (map.get("paths") instanceof String) {
                    drawing.setImagePath(map.get("paths").toString());
                    drawing.setPaths(new ArrayList<>());
                }
            }
            return drawing;
        }
        return null;
    }
    
    /**
     * Convert a list of raw objects to Lobby objects
     * @param rawObjects List of objects to convert
     * @return List of converted Lobby objects
     */
    public List<Lobby> convertToLobbyList(Object rawObjects) {
        List<Lobby> result = new ArrayList<>();
        
        if (rawObjects instanceof List) {
            List<?> rawList = (List<?>) rawObjects;
            
            for (Object rawObject : rawList) {
                Lobby lobby = convertToLobby(rawObject);
                if (lobby != null) {
                    result.add(lobby);
                }
            }
        }
        return result;
    }
}
