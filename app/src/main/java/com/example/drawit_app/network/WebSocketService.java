package com.example.drawit_app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.message.ConnectionStatusMessage;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbiesUpdateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.message.WebSocketMessage;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket service for real-time communication during gameplay
 */
public class WebSocketService {
    private static final String TAG = "WebSocketService";
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    
    private String wsUrl;
    private String authToken;
    private OkHttpClient client;
    private WebSocket webSocket;
    private WebSocketListener listener;
    private Moshi moshi;
    private WebSocketCallback callback;
    private LobbyUpdateCallback lobbyUpdateCallback;
    private GameUpdateCallback gameUpdateCallback;
    private String currentLobbyId = null;
    
    // Connection state tracking
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean reconnectEnabled = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 3000; // 3 seconds
        private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    
    // Message converter utility
    private com.example.drawit_app.util.WebSocketMessageConverter messageConverter;
    
    /**
     * Callback interface for WebSocket events
     */
    public interface WebSocketCallback {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onLobbyStateChanged(LobbyStateMessage message);
        void onGameStateChanged(GameStateMessage message);
        void onLobbiesUpdated(LobbiesUpdateMessage message);
        void onError(String errorMessage);
    }
    
    /**
     * Callback interface for Lobby state updates
     * Enhanced to include game state messages for better lobby-to-game transitions
     */
    public interface LobbyUpdateCallback {
        void onLobbyStateChanged(LobbyStateMessage message);
        void onLobbiesUpdated(LobbiesUpdateMessage message);
        void onGameStateChanged(GameStateMessage message);
        void onError(String errorMessage);
    }
    
    /**
     * Callback interface for Game state updates
     */
    public interface GameUpdateCallback {
        void onGameStateChanged(GameStateMessage message);
        void onError(String errorMessage);
    }
    
    public WebSocketService(String wsUrl, String authToken, WebSocketCallback callback) {
        this.wsUrl = wsUrl;
        this.authToken = authToken;
        this.callback = callback;
        
        // Initialize Moshi for JSON serialization/deserialization
        this.moshi = new Moshi.Builder()
                .add(PolymorphicJsonAdapterFactory.of(WebSocketMessage.class, "type")
                        .withSubtype(LobbyStateMessage.class, "lobby_state")
                        .withSubtype(GameStateMessage.class, "game_state")
                        .withSubtype(LobbiesUpdateMessage.class, "lobbies_update")
                        .withSubtype(ConnectionStatusMessage.class, "connection_established"))
                .build();
        
        // Initialize message converter
        this.messageConverter = new com.example.drawit_app.util.WebSocketMessageConverter(moshi);
        
        // Initialize OkHttp client
        this.client = new OkHttpClient.Builder().build();
        
        // Initialize WebSocket listener
        this.listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.i(TAG, "🔊 WEBSOCKET CONNECTED SUCCESSFULLY");
                isConnected = true;
                isConnecting = false;
                reconnectAttempts = 0; // Reset reconnect attempts on successful connection
                
                if (callback != null) {
                    callback.onConnected();
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    // Enhanced detailed logging for WebSocket message receipt
                    Log.i(TAG, "📥 WEBSOCKET MESSAGE RECEIVED - Length: " + text.length() + " bytes");
                    
                    // Always log message receipt timestamp for correlation with server logs
                    String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS")
                        .format(new java.util.Date());
                    Log.i(TAG, "⏰ WebSocket message received at: " + timestamp);
                    
                    // Extract message type using the converter
                    String messageType = messageConverter.extractMessageType(text);
                    Log.i(TAG, "📋 Message Type: " + messageType);
                    
                    // Log part of the raw message for debugging (limit length to avoid huge logs)
                    String logSample = text.length() > 200 ? text.substring(0, 200) + "..." : text;
                    Log.d(TAG, "📄 Raw message sample: " + logSample);
                    
                    // Track processing time
                    long startTime = System.currentTimeMillis();
                    
                    // Dispatch based on message type
                    switch (messageType) {
                        case "lobby_state":
                            Log.i(TAG, "🎮 Processing LOBBY_STATE message");
                            handleLobbyStateMessage(text);
                            break;
                            
                        case "lobbies_update":
                            Log.i(TAG, "🎮 Processing LOBBIES_UPDATE message");
                            handleLobbiesUpdateMessage(text);
                            break;
                            
                        case "game_state":
                            Log.i(TAG, "🎮 Processing GAME_STATE message");
                            handleGameStateMessage(text);
                            break;
                            
                        case "connection_established":
                            Log.i(TAG, "🎮 Processing CONNECTION_ESTABLISHED message");
                            handleConnectionEstablishedMessage(text);
                            break;
                            
                        case "drawing_update":
                            Log.i(TAG, "🎮 Processing DRAWING_UPDATE message");
                            handleDrawingUpdateMessage(text);
                            break;
                            
                        default:
                            Log.w(TAG, "❓ Unknown message type: " + messageType);
                            notifyError("Unknown message type: " + messageType);
                            break;
                    }
                    
                    // Log processing time
                    long processingTime = System.currentTimeMillis() - startTime;
                    Log.i(TAG, "⏱️ WebSocket message processing completed in " + processingTime + "ms");
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to process WebSocket message: " + e.getMessage(), e);
                    notifyError("Failed to process WebSocket message: " + e.getMessage());
                }
            }
            
            /**
             * Handle lobby state messages
             */
            private void handleLobbyStateMessage(String json) {
                try {
                    Log.i(TAG, "Processing lobby_state message");
                    LobbyStateMessage message = messageConverter.parseLobbyStateMessage(json);
                    if (message == null) {
                        Log.e(TAG, "Failed to parse lobby_state message");
                        return;
                    }
                    
                    // Log detailed information about the message
                    String event = message.getLobbyPayload() != null ? message.getLobbyPayload().getEvent() : null;
                    boolean isPlayerEvent = event != null && (event.equals("player_joined") || event.equals("player_left"));
                    
                    if (isPlayerEvent) {
                        Log.i(TAG, " RECEIVED PLAYER EVENT in WebSocketService: " + event);
                        if (message.getLobbyPayload().getLobby() != null && message.getLobbyPayload().getLobby().getPlayers() != null) {
                            Log.i(TAG, " Player count: " + message.getLobbyPayload().getLobby().getPlayers().size());
                            for (User player : message.getLobbyPayload().getLobby().getPlayers()) {
                                Log.i(TAG, " Player: " + player.getUsername() + " (ID: " + player.getUserId() + ")");
                            }
                        }
                    }
                    
                    // Update current lobby ID if needed
                    if (message.getLobbyPayload().getLobby() != null) {
                        currentLobbyId = message.getLobbyPayload().getLobby().getLobbyId();
                    }
                    
                    // Notify callbacks
                    boolean mainCallbackRegistered = callback != null;
                    boolean lobbyCallbackRegistered = lobbyUpdateCallback != null;
                    Log.i(TAG, "WebSocket callbacks: Main callback registered: " + mainCallbackRegistered + 
                             ", Lobby callback registered: " + lobbyCallbackRegistered);
                     
                    if (callback != null) {
                        Log.i(TAG, " Notifying main callback of lobby state change");
                        callback.onLobbyStateChanged(message);
                    }
                    if (lobbyUpdateCallback != null) {
                        Log.i(TAG, " Notifying lobby callback of lobby state change");
                        lobbyUpdateCallback.onLobbyStateChanged(message);
                    }
                    
                    if (!mainCallbackRegistered && !lobbyCallbackRegistered) {
                        Log.e(TAG, " NO CALLBACKS REGISTERED - lobby updates will not be processed!");
                    }
                    
                    Log.d(TAG, "Successfully processed lobby_state message");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing lobby_state message: " + e.getMessage(), e);
                    notifyError("Error processing lobby_state message: " + e.getMessage());
                }
            }
            
            /**
             * Handle lobbies update messages
             */
            private void handleLobbiesUpdateMessage(String json) {
                try {
                    Log.i(TAG, "Processing lobbies_update message");
                    LobbiesUpdateMessage message = messageConverter.parseLobbiesUpdateMessage(json);
                    if (message == null) {
                        Log.e(TAG, "Failed to parse lobbies_update message");
                        return;
                    }
                    
                    // Fix for ClassCastException: ensure all objects are properly converted to Lobby instances
                    if (message.getLobbiesPayload() != null && message.getLobbiesPayload().getLobbies() != null) {
                        try {
                            // Log lobby count for debugging
                            Log.i(TAG, "📊 Lobbies update includes " + message.getLobbiesPayload().getLobbies().size() + " lobbies");
                            
                            // Immediately convert any potential LinkedHashTreeMap objects to proper Lobby objects
                            // This is necessary because Moshi might not properly deserialize nested objects
                            List<Object> rawLobbies = Collections.singletonList(message.getLobbiesPayload().getLobbies());
                            List<Lobby> properLobbies = new ArrayList<>();
                            
                            for (Object rawLobby : rawLobbies) {
                                try {
                                    // If it's already a Lobby instance, use it directly
                                    if (rawLobby instanceof Lobby) {
                                        properLobbies.add((Lobby) rawLobby);
                                    } 
                                    // If it's a map, convert it to a Lobby object using Moshi
                                    else if (rawLobby instanceof Map) {
                                        Map<String, Object> lobbyMap = (Map<String, Object>) rawLobby;
                                        
                                        // Use Moshi to properly convert the map to a Lobby object
                                        String lobbyJson = moshi.adapter(Map.class).toJson(lobbyMap);
                                        Lobby convertedLobby = moshi.adapter(Lobby.class).fromJson(lobbyJson);
                                        
                                        if (convertedLobby != null) {
                                            properLobbies.add(convertedLobby);
                                            Log.d(TAG, "✅ Successfully converted map to Lobby object: " + 
                                                  convertedLobby.getLobbyName());
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "❌ Error converting lobby object: " + e.getMessage());
                                }
                            }
                            
                            // Replace the lobbies list with our properly converted list
                            message.getLobbiesPayload().setLobbies(properLobbies);
                            
                            // Now safely log the first few lobbies
                            int count = 0;
                            for (Lobby lobby : properLobbies) {
                                if (count++ < 3) { // Only log first 3 lobbies to avoid excessive logging
                                    Log.d(TAG, "🏠 Lobby: " + lobby.getLobbyName() + ", ID: " + lobby.getLobbyId() + 
                                          ", Players: " + (lobby.getPlayers() != null ? lobby.getPlayers().size() : 0));
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing lobbies list: " + e.getMessage(), e);
                        }
                    }
                    
                    boolean handled = false;
                    
                    // Notify main callback
                    if (callback != null) {
                        callback.onLobbiesUpdated(message);
                        handled = true;
                    }
                    
                    // Notify lobby update callback
                    if (lobbyUpdateCallback != null) {
                        // Direct notification
                        lobbyUpdateCallback.onLobbiesUpdated(message);
                        handled = true;
                        
                        // Also convert to lobby_state if possible for backward compatibility
                        if (message.getLobbiesPayload() != null && currentLobbyId != null) {
                            // Use the converter to safely convert lobbies
                            List<Lobby> lobbies = messageConverter.convertToLobbyList(message.getLobbiesPayload().getLobbies());
                            
                            // Find the current lobby and create a lobby state message
                            for (Lobby lobby : lobbies) {
                                if (currentLobbyId.equals(lobby.getLobbyId())) {
                                    LobbyStateMessage lobbyStateMsg = new LobbyStateMessage(lobby);
                                    lobbyUpdateCallback.onLobbyStateChanged(lobbyStateMsg);
                                    Log.d(TAG, "Successfully converted lobbies_update to lobby_state for current lobby");
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (!handled) {
                        Log.d(TAG, "No callback registered to handle lobbies_update");
                    } else {
                        Log.d(TAG, "Successfully processed lobbies_update message");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing lobbies_update message: " + e.getMessage(), e);
                    notifyError("Error processing lobbies_update message: " + e.getMessage());
                }
            }
            
            /**
             * Handle game state messages
             */
            private void handleGameStateMessage(String json) {
                try {
                    // Use message converter to parse the game state message
                    GameStateMessage message = messageConverter.parseGameStateMessage(json);
                    
                    if (message == null) {
                        Log.e(TAG, "Failed to parse game_state message");
                        return;
                    }
                    
                    // Log detailed info about the game state message
                    if (message.getGamePayload() != null && message.getGamePayload().getGame() != null) {
                        Log.i(TAG, "🎲 Game State message received for game ID: " + 
                              message.getGamePayload().getGame().getGameId());
                    }
                    
                    // Notify main callback
                    if (callback != null) {
                        Log.d(TAG, "Notifying main callback of game state change");
                        callback.onGameStateChanged(message);
                    }
                    
                    // Notify game update callback
                    if (gameUpdateCallback != null) {
                        Log.d(TAG, "Notifying game update callback of game state change");
                        gameUpdateCallback.onGameStateChanged(message);
                    }
                    
                    // Also notify lobby update callback to handle lobby-to-game transitions
                    // This is critical for non-host players to detect game start
                    if (lobbyUpdateCallback != null) {
                        Log.d(TAG, "Notifying lobby update callback of game state change");
                        lobbyUpdateCallback.onGameStateChanged(message);
                    }
                    
                    Log.d(TAG, "Successfully processed game_state message");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing game_state message: " + e.getMessage(), e);
                    notifyError("Error processing game_state message: " + e.getMessage());
                }
            }
            
            /**
             * Handle connection established messages
             */
            private void handleConnectionEstablishedMessage(String json) {
                try {
                    // Use message converter to parse the connection established message
                    ConnectionStatusMessage message = messageConverter.parseConnectionStatusMessage(json);
                    
                    if (message == null) {
                        Log.e(TAG, "Failed to parse connection_established message");
                        return;
                    }
                    
                    // Notify main callback
                    if (callback != null) {
                        callback.onConnected();
                    }
                    
                    Log.d(TAG, "Successfully processed connection_established message");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing connection_established message: " + e.getMessage(), e);
                    notifyError("Error processing connection_established message: " + e.getMessage());
                }
            }
            
            /**
             * Handle drawing update messages
             */
            private void handleDrawingUpdateMessage(String json) {
                // Implement if needed
                Log.d(TAG, "Drawing update message received (not fully implemented)");
            }
            
            /**
             * Notify all callbacks about an error
             */
            private void notifyError(String errorMessage) {
                if (callback != null) {
                    callback.onError(errorMessage);
                }
                
                if (lobbyUpdateCallback != null) {
                    lobbyUpdateCallback.onError(errorMessage);
                }
                
                if (gameUpdateCallback != null) {
                    gameUpdateCallback.onError(errorMessage);
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                // Binary messages not used in this application
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.i(TAG, "🚫 WEBSOCKET CLOSING: code=" + code + ", reason=" + reason);
                isConnected = false;
                webSocket.close(NORMAL_CLOSURE_STATUS, null);
                if (callback != null) {
                    callback.onDisconnected(code, reason);
                }
                
                // Try to reconnect if not explicitly closed by the app
                if (reconnectEnabled && code != NORMAL_CLOSURE_STATUS) {
                    attemptReconnect();
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "⚠️ WEBSOCKET FAILURE: " + t.getMessage(), t);
                isConnected = false;
                isConnecting = false;
                
                // Detailed error reporting
                String responseInfo = "";
                if (response != null) {
                    responseInfo = " (HTTP " + response.code() + ": " + response.message() + ")";
                }
                String errorMsg = "WebSocket connection error" + responseInfo + ": " + t.getMessage();
                
                // Notify callbacks
                if (callback != null) {
                    callback.onError(errorMsg);
                }
                if (lobbyUpdateCallback != null) {
                    lobbyUpdateCallback.onError(errorMsg);
                }
                if (gameUpdateCallback != null) {
                    gameUpdateCallback.onError(errorMsg);
                }
                
                // Try to reconnect
                if (reconnectEnabled) {
                    attemptReconnect();
                }
            }
        };
    }
    
    /**
     * Connect to the WebSocket server with improved authentication validation and state tracking
     */
    public void connect() {
        // Don't attempt to connect if already connected or connecting
        if (isConnected) {
            Log.i(TAG, "ℹ️ WebSocket already connected");
            return;
        }
        
        if (isConnecting) {
            Log.i(TAG, "ℹ️ WebSocket connection already in progress");
            return;
        }
        
        // Validate authentication token before connecting
        if (authToken == null || authToken.isEmpty() || authToken.equals("null")) {
            Log.e(TAG, "⚠️ Cannot connect to WebSocket: Authentication token is null or empty");
            if (callback != null) {
                callback.onError("Authentication token is missing or invalid");
            }
            return;
        }
        
        Log.i(TAG, "🔌 Connecting to WebSocket server: " + wsUrl);
        Log.d(TAG, "🔑 Using auth token: " + (authToken.length() > 5 ? 
              authToken.substring(0, 5) + "..." : "[short token]"));
        
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + authToken)
                .build();
        
        isConnecting = true;
        webSocket = client.newWebSocket(request, listener);
        Log.i(TAG, "🔄 WebSocket connection request sent");
    }
    
    /**
     * Disconnect from the WebSocket server
     */
    public void disconnect() {
        reconnectEnabled = false; // Disable reconnection attempts
        isConnected = false;
        isConnecting = false;
        
        // Cancel any pending reconnection attempts
        reconnectHandler.removeCallbacksAndMessages(null);
        
        if (webSocket != null) {
            Log.i(TAG, " MANUALLY CLOSING WebSocket connection");
            webSocket.close(NORMAL_CLOSURE_STATUS, "Closing connection");
            webSocket = null;
        }
    }
    
    /**
     * Attempt to reconnect to the WebSocket server
     */
    private void attemptReconnect() {
        if (!reconnectEnabled || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                Log.e(TAG, " MAX RECONNECTION ATTEMPTS REACHED ("+MAX_RECONNECT_ATTEMPTS+")");
                // Notify about reconnection failure
                if (callback != null) {
                    callback.onError("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
                }
            }
            return;
        }
        
        reconnectAttempts++;
        int delay = RECONNECT_DELAY_MS * reconnectAttempts; // Exponential backoff
        
        Log.i(TAG, " Attempting to reconnect in " + (delay/1000) + " seconds (attempt " + 
              reconnectAttempts + " of " + MAX_RECONNECT_ATTEMPTS + ")");
        
        reconnectHandler.postDelayed(() -> {
            if (!isConnected && !isConnecting) {
                Log.i(TAG, " Executing reconnection attempt " + reconnectAttempts);
                connect();
            }
        }, delay);
    }
    
    /**
     * Check if the WebSocket is currently connected
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        // Check both the connection state flag and that the socket exists
        return isConnected && webSocket != null;
    }
    
    /**
     * Send a drawing to all players in the game
     */
    public void sendDrawing(String gameId, Drawing drawing) {
        if (webSocket != null) {
            try {
                WebSocketMessage message = new WebSocketMessage("drawing", drawing);
                String json = moshi.adapter(WebSocketMessage.class).toJson(message);
                webSocket.send(json);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to send drawing: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Send a rating for a drawing
     */
    public void sendRating(String gameId, String drawingId, float rating) {
        if (webSocket != null) {
            try {
                WebSocketMessage message = new WebSocketMessage("rating", 
                        new WebSocketMessage.RatingPayload(gameId, drawingId, rating));
                String json = moshi.adapter(WebSocketMessage.class).toJson(message);
                webSocket.send(json);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to send rating: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Join a lobby via WebSocket
     */
    public void joinLobby(String lobbyId) {
        if (webSocket != null) {
            try {
                // Store the current lobby ID for lobbies_update conversion
                setCurrentLobbyId(lobbyId);
                
                WebSocketMessage message = new WebSocketMessage("join_lobby", 
                        new WebSocketMessage.LobbyPayload(lobbyId));
                String json = moshi.adapter(WebSocketMessage.class).toJson(message);
                webSocket.send(json);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to join lobby: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Leave a lobby via WebSocket
     */
    public void leaveLobby(String lobbyId) {
        if (webSocket != null) {
            try {
                // Clear current lobby ID when leaving
                if (currentLobbyId != null && currentLobbyId.equals(lobbyId)) {
                    setCurrentLobbyId(null);
                }
                
                WebSocketMessage message = new WebSocketMessage("leave_lobby", 
                        new WebSocketMessage.LobbyPayload(lobbyId));
                String json = moshi.adapter(WebSocketMessage.class).toJson(message);
                webSocket.send(json);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to leave lobby: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Start a game via WebSocket
     */
    public void startGame(String lobbyId) {
        if (webSocket != null) {
            try {
                WebSocketMessage message = new WebSocketMessage("start_game", 
                        new WebSocketMessage.LobbyPayload(lobbyId));
                String json = moshi.adapter(WebSocketMessage.class).toJson(message);
                webSocket.send(json);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to start game: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Set lobby update callback for real-time lobby updates
     */
    public void setLobbyUpdateCallback(LobbyUpdateCallback lobbyUpdateCallback) {
        this.lobbyUpdateCallback = lobbyUpdateCallback;
    }
    
    /**
     * Set game update callback for real-time game updates
     */
    public void setGameUpdateCallback(GameUpdateCallback callback) {
        this.gameUpdateCallback = callback;
    }
    
    /**
     * Remove lobby update callback when no longer needed
     * @param callback The callback to remove
     */
    public void removeLobbyUpdateCallback(LobbyUpdateCallback callback) {
        if (this.lobbyUpdateCallback == callback) {
            this.lobbyUpdateCallback = null;
            Log.d(TAG, "Lobby update callback removed");
        }
    }
    
    /**
     * Sets the current lobby ID for context in message handling
     */
    public void setCurrentLobbyId(String lobbyId) {
        this.currentLobbyId = lobbyId;
    }
    
    /**
     * Set authentication token for WebSocket connections
     * If already connected with a different token, disconnects and reconnects
     * @param token the new authentication token
     */
    public void setAuthToken(String token) {
        // Don't do anything if token hasn't actually changed
        if (Objects.equals(this.authToken, token)) {
            return;
        }
        
        String oldToken = this.authToken;
        this.authToken = token;
        
        Log.i(TAG, "🔑 Auth token updated" + 
              (token != null ? ": " + token.substring(0, Math.min(5, token.length())) + "..." : ": null"));
        
        // If we have a new valid token and were previously connected with invalid token,
        // or currently have an active WebSocket, disconnect and reconnect with new token
        boolean hadInvalidToken = oldToken == null || oldToken.isEmpty() || "null".equals(oldToken);
        boolean hasValidToken = token != null && !token.isEmpty() && !"null".equals(token);
        
        if (hasValidToken && (webSocket != null || isConnected || isConnecting)) {
            Log.i(TAG, "🔄 Reconnecting with new auth token");
            // Only disconnect if we're currently connected or connecting
            if (webSocket != null || isConnected || isConnecting) {
                disconnect(); // Closes existing connection if any
            }
            
            // Reconnect with new token
            connect();
        } else if (!hasValidToken && (isConnected || webSocket != null)) {
            // Invalid token provided but we're connected - disconnect
            Log.w(TAG, "⛔ Invalid token provided - disconnecting WebSocket");
            disconnect();
        }
    }
    
    // isConnected method was moved to the top of the class
    
    /**
     * Handle a lobby state message by dispatching to callbacks
     * with detailed logging for debugging
     */
    private void handleLobbyStateMessage(LobbyStateMessage lobbyMsg) {
        // Check main callback
        if (callback != null) {
            Log.d(TAG, "CALLING main callback onLobbyStateChanged...");
            try {
                callback.onLobbyStateChanged(lobbyMsg);
                Log.d(TAG, "main callback.onLobbyStateChanged COMPLETED");
            } catch (Exception e) {
                Log.e(TAG, "CRITICAL ERROR: main callback.onLobbyStateChanged FAILED: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "CRITICAL ERROR: main callback is NULL");
        }
        
        // Check lobby update callback
        if (lobbyUpdateCallback != null) {
            Log.d(TAG, "CALLING lobbyUpdateCallback.onLobbyStateChanged... " + 
                  lobbyUpdateCallback.getClass().getName());
            try {
                lobbyUpdateCallback.onLobbyStateChanged(lobbyMsg);
                Log.d(TAG, "lobbyUpdateCallback.onLobbyStateChanged COMPLETED");
            } catch (Exception e) {
                Log.e(TAG, "CRITICAL ERROR: lobbyUpdateCallback.onLobbyStateChanged FAILED: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "CRITICAL ERROR: lobbyUpdateCallback is NULL - NO LOBBY UPDATES WILL BE PROCESSED");
        }
    }
    
    /**
     * Manually parse a JSON string into a LobbyStateMessage
     * This avoids Moshi class casting issues by explicitly constructing the object hierarchy
     */
    private LobbyStateMessage parseLobbyStateMessageFromJson(String jsonStr) throws JSONException {
        Log.d(TAG, "Manual parsing of lobby_state message");
        JSONObject json = new JSONObject(jsonStr);
        
        // Extract the payload object
        JSONObject payloadJson = json.getJSONObject("payload");
        Log.d(TAG, "Extracted payload from message");
        
        // Parse the lobby object
        JSONObject lobbyJson = payloadJson.getJSONObject("lobby");
        String lobbyId = lobbyJson.getString("lobbyId");
        String lobbyName = lobbyJson.getString("name");
        String hostId = lobbyJson.optString("hostId", "");
        int maxPlayers = lobbyJson.getInt("maxPlayers");
        int numRounds = lobbyJson.optInt("numRounds", 3);
        int roundDurationSeconds = lobbyJson.optInt("roundDurationSeconds", 60);
        
        // Create Lobby object with the correct constructor
        Lobby lobby = new Lobby(lobbyId, lobbyName, hostId, maxPlayers, numRounds, roundDurationSeconds);
        Log.d(TAG, "Created Lobby object: id=" + lobbyId + ", name=" + lobbyName);
        
        // Parse players list
        List<User> players = new ArrayList<>();
        if (payloadJson.has("players")) {
            JSONArray playersJson = payloadJson.getJSONArray("players");
            for (int i = 0; i < playersJson.length(); i++) {
                JSONObject playerJson = playersJson.getJSONObject(i);
                String userId = playerJson.getString("userId");
                String username = playerJson.getString("username");
                String avatarUrl = playerJson.optString("avatarUrl", null);
                User player = new User(userId, username, avatarUrl);
                players.add(player);
            }
            Log.d(TAG, "Parsed players list: " + players.size() + " players");
        }
        
        // Parse host user
        User hostUser = null;
        if (payloadJson.has("hostUser")) {
            JSONObject hostJson = payloadJson.getJSONObject("hostUser");
            String hostUserId = hostJson.getString("userId");
            String hostUsername = hostJson.getString("username");
            String hostAvatarUrl = hostJson.optString("avatarUrl", null);
            hostUser = new User(hostUserId, hostUsername, hostAvatarUrl);
            Log.d(TAG, "Parsed host user: " + hostUsername);
        }
        
        // Parse event and userId if present
        String event = payloadJson.optString("event", "");
        String userId = payloadJson.optString("userId", "");
        
        // Create LobbyPayload
        LobbyStateMessage.LobbyPayload payload = new LobbyStateMessage.LobbyPayload();
        payload.setLobby(lobby);
        payload.setPlayers(players);
        payload.setHostUser(hostUser);
        payload.setEvent(event);
        payload.setUserId(userId);
        
        // Create LobbyStateMessage
        LobbyStateMessage message = new LobbyStateMessage(lobby);
        message.setLobbyPayload(payload);
        Log.d(TAG, "Successfully created LobbyStateMessage with proper types");
        
        return message;
    }
}
