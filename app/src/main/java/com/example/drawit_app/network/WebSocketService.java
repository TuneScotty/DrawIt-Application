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

// Service that manages WebSocket connections for real-time game communication
public class WebSocketService {
    private static final String TAG = "WebSocketService";
    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private final String wsUrl;
    private String authToken;
    private final OkHttpClient client;
    private WebSocket webSocket;
    private final WebSocketListener listener;
    private final Moshi moshi;
    private final WebSocketCallback callback;
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
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // Message converter utility
    private final com.example.drawit_app.util.WebSocketMessageConverter messageConverter;

    // Callback interface for general WebSocket events
    public interface WebSocketCallback {
        void onConnected();

        void onDisconnected(int code, String reason);

        void onLobbyStateChanged(LobbyStateMessage message);

        void onGameStateChanged(GameStateMessage message);

        void onLobbiesUpdated(LobbiesUpdateMessage message);

        void onError(String errorMessage);
    }

    // Callback interface for Lobby state updates
    public interface LobbyUpdateCallback {
        void onLobbyStateChanged(LobbyStateMessage message);

        void onLobbiesUpdated(LobbiesUpdateMessage message);

        void onGameStateChanged(GameStateMessage message);

        void onError(String errorMessage);
    }

    // Callback interface for Game state updates
    public interface GameUpdateCallback {
        void onGameStateChanged(GameStateMessage message);

        void onError(String errorMessage);
    }

    public WebSocketService(String wsUrl, String authToken, WebSocketCallback callback) {
        this.wsUrl = wsUrl;
        this.authToken = authToken;
        this.callback = callback;

        this.moshi = new Moshi.Builder()
                .add(PolymorphicJsonAdapterFactory.of(WebSocketMessage.class, "type")
                        .withSubtype(LobbyStateMessage.class, "lobby_state")
                        .withSubtype(GameStateMessage.class, "game_state")
                        .withSubtype(LobbiesUpdateMessage.class, "lobbies_update")
                        .withSubtype(ConnectionStatusMessage.class, "connection_established"))
                .build();

        this.messageConverter = new com.example.drawit_app.util.WebSocketMessageConverter(moshi);
        this.client = new OkHttpClient.Builder().build();
        this.listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.i(TAG, "WebSocket connected");
                isConnected = true;
                isConnecting = false;
                reconnectAttempts = 0;

                if (callback != null) {
                    callback.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    String messageType = messageConverter.extractMessageType(text);
                    Log.d(TAG, "WebSocket message received: " + messageType);

                    switch (messageType) {
                        case "lobby_state":
                            handleLobbyStateMessage(text);
                            break;

                        case "lobbies_update":
                            handleLobbiesUpdateMessage(text);
                            break;

                        case "game_state":
                            handleGameStateMessage(text);
                            break;

                        case "connection_established":
                            handleConnectionEstablishedMessage(text);
                            break;

                        case "drawing_update":
                            handleDrawingUpdateMessage(text);
                            break;

                        default:
                            Log.w(TAG, "Unknown message type: " + messageType);
                            notifyError("Unknown message type: " + messageType);
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process WebSocket message: " + e.getMessage());
                    notifyError("Failed to process WebSocket message: " + e.getMessage());
                }
            }

            // Process a lobby state message and notify registered callbacks
            private void handleLobbyStateMessage(String json) {
                try {
                    LobbyStateMessage message = messageConverter.parseLobbyStateMessage(json);
                    if (message == null) {
                        Log.e(TAG, "Failed to parse lobby_state message");
                        return;
                    }

                    // Update current lobby ID if needed
                    if (message.getLobbyPayload() != null && message.getLobbyPayload().getLobby() != null) {
                        currentLobbyId = message.getLobbyPayload().getLobby().getLobbyId();
                    }

                    // Notify callbacks
                    if (callback != null) {
                        callback.onLobbyStateChanged(message);
                    }
                    if (lobbyUpdateCallback != null) {
                        lobbyUpdateCallback.onLobbyStateChanged(message);
                    }

                    if (callback == null && lobbyUpdateCallback == null) {
                        Log.e(TAG, "No callbacks registered for lobby updates");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing lobby_state message: " + e.getMessage());
                    notifyError("Error processing lobby_state message: " + e.getMessage());
                }
            }

            // Process lobbies update messages and notify registered callbacks
            private void handleLobbiesUpdateMessage(String json) {
                try {
                    LobbiesUpdateMessage message = messageConverter.parseLobbiesUpdateMessage(json);
                    if (message == null) {
                        Log.e(TAG, "Failed to parse lobbies_update message");
                        return;
                    }

                    // Convert any potential map objects to proper Lobby instances
                    if (message.getLobbiesPayload() != null && message.getLobbiesPayload().getLobbies() != null) {
                        List<Lobby> lobbies = messageConverter.convertToLobbyList(message.getLobbiesPayload().getLobbies());
                        message.getLobbiesPayload().setLobbies(lobbies);
                    }

                    // Notify callbacks
                    if (callback != null) {
                        callback.onLobbiesUpdated(message);
                    }

                    if (lobbyUpdateCallback != null) {
                        lobbyUpdateCallback.onLobbiesUpdated(message);

                        // Also convert to lobby_state for current lobby if possible
                        if (message.getLobbiesPayload() != null && currentLobbyId != null) {
                            List<Lobby> lobbies = message.getLobbiesPayload().getLobbies();
                            for (Lobby lobby : lobbies) {
                                if (currentLobbyId.equals(lobby.getLobbyId())) {
                                    lobbyUpdateCallback.onLobbyStateChanged(new LobbyStateMessage(lobby));
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing lobbies_update message: " + e.getMessage());
                    notifyError("Error processing lobbies_update message: " + e.getMessage());
                }
            }

            // Process game state messages and notify all relevant callbacks
            private void handleGameStateMessage(String json) {
                try {
                    GameStateMessage message = messageConverter.parseGameStateMessage(json);

                    if (message == null) {
                        Log.e(TAG, "Failed to parse game_state message");
                        return;
                    }

                    // Notify all relevant callbacks
                    if (callback != null) {
                        callback.onGameStateChanged(message);
                    }

                    if (gameUpdateCallback != null) {
                        gameUpdateCallback.onGameStateChanged(message);
                    }

                    // Also notify lobby callback for lobby-to-game transitions
                    if (lobbyUpdateCallback != null) {
                        lobbyUpdateCallback.onGameStateChanged(message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing game_state message: " + e.getMessage());
                    notifyError("Error processing game_state message: " + e.getMessage());
                }
            }

            // Process connection established messages
            private void handleConnectionEstablishedMessage(String json) {
                try {
                    ConnectionStatusMessage message = messageConverter.parseConnectionStatusMessage(json);

                    if (message == null) {
                        Log.e(TAG, "Failed to parse connection_established message");
                        return;
                    }

                    if (callback != null) {
                        callback.onConnected();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing connection_established message: " + e.getMessage());
                    notifyError("Error processing connection_established message: " + e.getMessage());
                }
            }

            // Process drawing update messages
            private void handleDrawingUpdateMessage(String json) {
                // Placeholder for future implementation
            }

            // Notify all registered callbacks about an error
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
                Log.i(TAG, "WebSocket closing: code=" + code + ", reason=" + reason);
                isConnected = false;
                webSocket.close(NORMAL_CLOSURE_STATUS, null);
                if (callback != null) {
                    callback.onDisconnected(code, reason);
                }

                if (reconnectEnabled && code != NORMAL_CLOSURE_STATUS) {
                    attemptReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                isConnected = false;
                isConnecting = false;

                String responseInfo = response != null ? " (HTTP " + response.code() + ")" : "";
                String errorMsg = "WebSocket connection error" + responseInfo + ": " + t.getMessage();

                notifyError(errorMsg);

                if (reconnectEnabled) {
                    attemptReconnect();
                }
            }
        };
    }

    // Connect to the WebSocket server with authentication validation
    public void connect() {
        if (isConnected) {
            Log.d(TAG, "WebSocket already connected");
            return;
        }

        if (isConnecting) {
            Log.d(TAG, "WebSocket connection already in progress");
            return;
        }

        // Validate authentication token before connecting
        if (authToken == null || authToken.isEmpty() || authToken.equals("null")) {
            Log.e(TAG, "Cannot connect to WebSocket: Invalid authentication token");
            if (callback != null) {
                callback.onError("Authentication token is missing or invalid");
            }
            return;
        }

        Log.i(TAG, "Connecting to WebSocket server: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + authToken)
                .build();

        isConnecting = true;
        webSocket = client.newWebSocket(request, listener);
    }

    // Disconnect from the WebSocket server
    public void disconnect() {
        reconnectEnabled = false;
        isConnected = false;
        isConnecting = false;

        reconnectHandler.removeCallbacksAndMessages(null);

        if (webSocket != null) {
            Log.i(TAG, "Closing WebSocket connection");
            webSocket.close(NORMAL_CLOSURE_STATUS, "Closing connection");
            webSocket = null;
        }
    }

    // Attempt to reconnect to the WebSocket server with exponential backoff
    private void attemptReconnect() {
        if (!reconnectEnabled || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                Log.e(TAG, "Maximum reconnection attempts reached");
                if (callback != null) {
                    callback.onError("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
                }
            }
            return;
        }

        reconnectAttempts++;
        int delay = RECONNECT_DELAY_MS * reconnectAttempts;

        Log.i(TAG, "Attempting to reconnect in " + (delay / 1000) + " seconds (attempt " +
                reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");

        reconnectHandler.postDelayed(() -> {
            if (!isConnected && !isConnecting) {
                connect();
            }
        }, delay);
    }

    /**
     * Check if the WebSocket is currently connected
     *
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
     *
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
     *
     * @param token the new authentication token
     */
    public void setAuthToken(String token) {
        // Don't do anything if token hasn't actually changed
        if (Objects.equals(this.authToken, token)) {
            return;
        }

        String oldToken = this.authToken;
        this.authToken = token;

        Log.i(TAG, "Auth token updated" +
                (token != null ? ": " + token.substring(0, Math.min(5, token.length())) + "..." : ": null"));

        // If we have a new valid token and were previously connected with invalid token,
        // or currently have an active WebSocket, disconnect and reconnect with new token
        boolean hadInvalidToken = oldToken == null || oldToken.isEmpty() || "null".equals(oldToken);
        boolean hasValidToken = token != null && !token.isEmpty() && !"null".equals(token);

        if (hasValidToken && (webSocket != null || isConnected || isConnecting)) {
            Log.i(TAG, "Reconnecting with new auth token");
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
}