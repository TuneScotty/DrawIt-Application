package com.example.drawit_app.util;

import android.util.Log;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.message.ConnectionEstablishedMessage;
import com.example.drawit_app.network.message.ConnectionStatusMessage;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbiesUpdateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.message.WebSocketMessage;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import android.util.Log;

import com.example.drawit_app.model.Drawing;
// DrawingPath and DrawingPoint are inner classes in the Drawing class
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.message.ConnectionStatusMessage;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbiesUpdateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.message.WebSocketMessage;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Utility class for converting WebSocket messages to domain models
 * Provides standardized methods for converting raw JSON data to typed objects
 * and handling common deserialization scenarios
 */
@Singleton
public class WebSocketMessageConverter {
    private static final String TAG = "WebSocketMessageConverter";
    private static final String DEFAULT_MESSAGE_TYPE = "unknown";
    
    private final Moshi moshi;
    
    @Inject
    public WebSocketMessageConverter(Moshi moshi) {
        this.moshi = moshi;
    }
    
    /**
     * Extract the message type from a raw WebSocket message
     * @param json Raw JSON message
     * @return Message type or "unknown" if not found
     */
    public String extractMessageType(String jsonMessage) {
        try {
            Log.d(TAG, "Extracting message type from WebSocket message");
            // Create type for Map<String, Object>
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = adapter.fromJson(jsonMessage);
            
            if (messageMap != null && messageMap.containsKey("type")) {
                String messageType = messageMap.get("type").toString();
                Log.i(TAG, "🔄 WebSocketMessageConverter: Extracted message type: " + messageType);
                return messageType;
            }
            
            Log.w(TAG, "🔄 WebSocketMessageConverter: No message type found, using default: " + DEFAULT_MESSAGE_TYPE);
            return DEFAULT_MESSAGE_TYPE;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting message type: " + e.getMessage(), e);
            return DEFAULT_MESSAGE_TYPE;
        }
    }
    
    /**
     * Parse a raw JSON message into a specific message type
     * @param json Raw JSON message
     * @param messageClass Class to parse to
     * @param <T> Type of message
     * @return Parsed message or null if parsing failed
     */
    public <T extends WebSocketMessage> T parseMessage(String json, Class<T> messageClass) {
        try {
            Log.d(TAG, "Parsing message to " + messageClass.getSimpleName());
            JsonAdapter<T> adapter = moshi.adapter(messageClass);
            T message = adapter.fromJson(json);
            
            if (message != null) {
                Log.i(TAG, "🔵 Successfully parsed message to " + messageClass.getSimpleName());
            } else {
                Log.e(TAG, "❌ Failed to parse message to " + messageClass.getSimpleName() + " - adapter returned null");
            }
            
            return message;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing message to " + messageClass.getSimpleName() + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parse a lobby state message
     * @param json Raw JSON message
     * @return Parsed LobbyStateMessage or null if parsing failed
     */
    public LobbyStateMessage parseLobbyStateMessage(String json) {
        try {
            Log.d(TAG, "Parsing lobby_state message");
            // Log part of the raw message for debugging
            String logSample = json.length() > 150 ? json.substring(0, 150) + "..." : json;
            Log.d(TAG, "Raw lobby_state message sample: " + logSample);
            
            JsonAdapter<LobbyStateMessage> adapter = moshi.adapter(LobbyStateMessage.class);
            LobbyStateMessage message = adapter.fromJson(json);
            
            if (message != null) {
                // Log successful parsing
                Log.i(TAG, "🔵 Successfully parsed lobby_state message");
                
                // Check if this contains a lobby payload
                if (message.getLobbyPayload() != null) {
                    String event = message.getLobbyPayload().getEvent();
                    Lobby lobby = message.getLobbyPayload().getLobby();
                    String lobbyId = lobby != null ? lobby.getLobbyId() : "unknown";
                    
                    Log.i(TAG, "🚨 LOBBY EVENT DETECTED: " + (event != null ? event : "update") + 
                          " for lobby " + lobbyId);
                    
                    // Detailed logging for player events (join/leave)
                    boolean isPlayerEvent = event != null && (event.equals("player_joined") || event.equals("player_left"));
                    if (isPlayerEvent && lobby != null) {
                        Log.i(TAG, "👤 PLAYER EVENT: " + event);
                        Log.i(TAG, "📊 Lobby " + lobbyId + " has " + 
                              (lobby.getPlayers() != null ? lobby.getPlayers().size() : 0) + " players");
                        
                        if (lobby.getPlayers() != null) {
                            Log.i(TAG, "📋 Player list:");
                            for (User player : lobby.getPlayers()) {
                                Log.i(TAG, "   - " + player.getUsername() + 
                                     " (ID: " + player.getUserId() + ")");
                            }
                        } else {
                            Log.w(TAG, "⚠️ Player list is NULL in a player event!");
                        }
                    }
                }
            } else {
                Log.e(TAG, "❌ Failed to parse lobby_state message - adapter returned null");
            }
            
            return message;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing lobby_state message: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parse a lobbies update message
     * @param json Raw JSON message
     * @return Parsed LobbiesUpdateMessage or null if parsing failed
     */
    public LobbiesUpdateMessage parseLobbiesUpdateMessage(String json) {
        return parseMessage(json, LobbiesUpdateMessage.class);
    }
    
    /**
     * Parse a game state message
     * @param json Raw JSON message
     * @return Parsed GameStateMessage or null if parsing failed
     */
    public GameStateMessage parseGameStateMessage(String json) {
        return parseMessage(json, GameStateMessage.class);
    }
    
    /**
     * Parse a connection status message
     * @param json Raw JSON message
     * @return Parsed ConnectionStatusMessage or null if parsing failed
     */
    public ConnectionStatusMessage parseConnectionStatusMessage(String json) {
        return parseMessage(json, ConnectionStatusMessage.class);
    }
    
    /**
     * Convert a raw object to a Lobby
     * Handles both direct Lobby objects and Map representations
     * @param rawObject Object to convert
     * @return Converted Lobby or null if conversion failed
     */
    public Lobby convertToLobby(Object rawObject) {
        if (rawObject instanceof Lobby) {
            return (Lobby) rawObject;
        }
        
        if (rawObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawObject;
            Lobby lobby = new Lobby();
            
            // Extract the basic fields from the map
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
            
            // Handle players list
            if (map.containsKey("players") && map.get("players") instanceof List) {
                List<?> rawPlayers = (List<?>) map.get("players");
                List<User> players = new ArrayList<>();
                
                for (Object rawPlayer : rawPlayers) {
                    User user = convertToUser(rawPlayer);
                    if (user != null) {
                        players.add(user);
                    }
                }
                
                lobby.setPlayers(players);
            }
            
            Log.d(TAG, "Successfully converted map to Lobby object: " + lobby.getLobbyName());
            return lobby;
        }
        
        return null;
    }
    
    /**
     * Convert a raw object to a User
     * @param rawObject Object to convert
     * @return Converted User or null if conversion failed
     */
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
    
    /**
     * Convert a raw object to a Game
     * @param rawObject Object to convert
     * @return Converted Game or null if conversion failed
     */
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
                    Game.GameState gameState = Game.GameState.valueOf(stateStr.toUpperCase());
                    game.setGameState(gameState);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid game state: " + stateStr, e);
                }
            }
            
            if (map.containsKey("currentRound") && map.get("currentRound") instanceof Number) {
                game.setCurrentRound(((Number) map.get("currentRound")).intValue());
            }
            
            // We're no longer processing rounds as a separate model object
            // Instead, game contains current round info directly
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
    
    // Round model has been removed from the project
    // Game state and round information should be accessed directly from the Game model
    
    /**
     * Convert a raw object to a Drawing
     * @param rawObject Object to convert
     * @return Converted Drawing or null if conversion failed
     */
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
                // Handle different possible formats for paths
                if (map.get("paths") instanceof List) {
                    // Direct list of path objects
                    List<?> rawPaths = (List<?>) map.get("paths");
                    List<Drawing.DrawingPath> drawingPaths = new ArrayList<>();
                    
                    for (Object rawPath : rawPaths) {
                        if (rawPath instanceof Map) {
                            Map<?, ?> pathMap = (Map<?, ?>) rawPath;
                            Drawing.DrawingPath path = new Drawing.DrawingPath();
                            
                            // Set path properties based on available data
                            if (pathMap.containsKey("color") && pathMap.get("color") instanceof String) {
                                try {
                                    // Try to parse color as int from hex string or direct integer
                                    String colorStr = pathMap.get("color").toString();
                                    if (colorStr.startsWith("#")) {
                                        path.setColor(android.graphics.Color.parseColor(colorStr));
                                    } else {
                                        path.setColor(Integer.parseInt(colorStr));
                                    }
                                } catch (Exception e) {
                                    // Fallback to default color (black)
                                    path.setColor(android.graphics.Color.BLACK);
                                    Log.e(TAG, "Failed to parse color: " + pathMap.get("color"), e);
                                }
                            }
                            
                            if (pathMap.containsKey("width") && pathMap.get("width") instanceof Number) {
                                path.setStrokeWidth(((Number) pathMap.get("width")).floatValue());
                            }
                            
                            if (pathMap.containsKey("points") && pathMap.get("points") instanceof List) {
                                List<?> pointsList = (List<?>) pathMap.get("points");
                                List<Drawing.PointF> points = new ArrayList<>();
                                
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
                    // Store the JSON string in the drawing's imagePath field for rendering
                    drawing.setImagePath(map.get("paths").toString());
                    // Initialize an empty paths list since we can't parse the string directly
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
        
        if (rawObjects == null) {
            return result;
        }
        
        try {
            if (rawObjects instanceof List) {
                List<?> rawList = (List<?>) rawObjects;
                
                for (Object item : rawList) {
                    Lobby lobby = convertToLobby(item);
                    if (lobby != null) {
                        result.add(lobby);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting raw object to lobby list", e);
        }
        
        return result;
    }
}
