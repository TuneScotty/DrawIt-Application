package com.example.drawit_app.network;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.message.WebSocketMessage;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory;

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
    
    /**
     * Callback interface for WebSocket events
     */
    public interface WebSocketCallback {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onLobbyStateChanged(LobbyStateMessage message);
        void onGameStateChanged(GameStateMessage message);
        void onError(String errorMessage);
    }
    
    /**
     * Callback interface for Lobby state updates
     */
    public interface LobbyUpdateCallback {
        void onLobbyStateChanged(LobbyStateMessage message);
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
                        .withSubtype(GameStateMessage.class, "game_state"))
                .build();
        
        // Initialize OkHttp client
        this.client = new OkHttpClient.Builder().build();
        
        // Initialize WebSocket listener
        this.listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (callback != null) {
                    callback.onConnected();
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    WebSocketMessage message = moshi.adapter(WebSocketMessage.class).fromJson(text);
                    if (message instanceof LobbyStateMessage) {
                        callback.onLobbyStateChanged((LobbyStateMessage) message);
                        if (lobbyUpdateCallback != null) {
                            lobbyUpdateCallback.onLobbyStateChanged((LobbyStateMessage) message);
                        }
                    } else if (message instanceof GameStateMessage) {
                        callback.onGameStateChanged((GameStateMessage) message);
                        if (gameUpdateCallback != null) {
                            gameUpdateCallback.onGameStateChanged((GameStateMessage) message);
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError("Failed to parse WebSocket message: " + e.getMessage());
                    }
                    if (lobbyUpdateCallback != null) {
                        lobbyUpdateCallback.onError("Failed to parse WebSocket message: " + e.getMessage());
                    }
                    if (gameUpdateCallback != null) {
                        gameUpdateCallback.onError("Failed to parse WebSocket message: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                // Binary messages not used in this application
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(NORMAL_CLOSURE_STATUS, null);
                if (callback != null) {
                    callback.onDisconnected(code, reason);
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (callback != null) {
                    callback.onError("WebSocket failure: " + t.getMessage());
                }
                if (lobbyUpdateCallback != null) {
                    lobbyUpdateCallback.onError("WebSocket failure: " + t.getMessage());
                }
                if (gameUpdateCallback != null) {
                    gameUpdateCallback.onError("WebSocket failure: " + t.getMessage());
                }
            }
        };
    }
    
    /**
     * Connect to the WebSocket server
     */
    public void connect() {
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + authToken)
                .build();
        webSocket = client.newWebSocket(request, listener);
    }
    
    /**
     * Disconnect from the WebSocket server
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(NORMAL_CLOSURE_STATUS, "Closing connection");
            webSocket = null;
        }
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
    public void setGameUpdateCallback(GameUpdateCallback gameUpdateCallback) {
        this.gameUpdateCallback = gameUpdateCallback;
    }
}
