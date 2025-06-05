package com.example.drawit_app.util;

import android.util.Log;

import com.example.drawit_app.model.Drawing;
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
import com.squareup.moshi.Types;

import java.lang.reflect.Type;
import java.util.ArrayList;
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
    public <T extends WebSocketMessage> T parseMessage(String json, Class<T> messageClass) {
        try {
            JsonAdapter<T> adapter = moshi.adapter(messageClass);
            return adapter.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message to " + messageClass.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    // Parse a lobby state message from JSON
    public LobbyStateMessage parseLobbyStateMessage(String json) {
        try {
            JsonAdapter<LobbyStateMessage> adapter = moshi.adapter(LobbyStateMessage.class);
            return adapter.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing lobby_state message: " + e.getMessage());
            return null;
        }
    }
    
    // Parse a lobbies update message
    public LobbiesUpdateMessage parseLobbiesUpdateMessage(String json) {
        return parseMessage(json, LobbiesUpdateMessage.class);
    }
    
    // Parse a game state message
    public GameStateMessage parseGameStateMessage(String json) {
        return parseMessage(json, GameStateMessage.class);
    }
    
    // Parse a connection status message
    public ConnectionStatusMessage parseConnectionStatusMessage(String json) {
        return parseMessage(json, ConnectionStatusMessage.class);
    }
    
    // Convert a raw object to a typed Lobby instance
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
                    Game.GameState gameState = Game.GameState.valueOf(stateStr.toUpperCase());
                    game.setGameState(gameState);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid game state: " + stateStr);
                }
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
