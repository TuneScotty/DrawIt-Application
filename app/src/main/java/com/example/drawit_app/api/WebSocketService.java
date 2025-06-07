package com.example.drawit_app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.api.message.ConnectionStatusMessage;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.api.message.LobbiesUpdateMessage;
import com.example.drawit_app.api.message.LobbyStateMessage;
import com.example.drawit_app.api.message.WebSocketMessage;
import com.example.drawit_app.model.User;
import com.example.drawit_app.repository.LobbyRepository;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.HashMap;
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
    private String activeGameId = null;
    private String pendingGameId = null;
    private String currentUserId = null; // New field for current user id
    private com.example.drawit_app.repository.LobbyRepository lobbyRepository = null;

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
        // lobbyRepository will be set later via setter to avoid circular dependency

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
                Log.d(TAG, "WebSocket message received: " + text);

                try {
                    JSONObject jsonObject = new JSONObject(text);
                    String type = jsonObject.optString("type", "");

                    switch (type) {
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
                        case "start_game":
                            handleStartGameMessage(text);
                            break;
                        case "error":
                            handleErrorMessage(text);
                            break;
                        case "lobby_joined":
                            handleLobbyJoinedMessage(text);
                            break;
                        default:
                            Log.w(TAG, "Unknown message type: " + type);
                            notifyError("Unknown message type: " + type);
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
                                    lobbyUpdateCallback.onLobbyStateChanged(new LobbyStateMessage());
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
            // Enhanced to ensure reliable delivery, especially for game start events
            private void handleGameStateMessage(String json) {
                try {
                    GameStateMessage message = messageConverter.parseGameStateMessage(json);

                    if (message == null) {
                        Log.e(TAG, "Failed to parse game_state message");
                        return;
                    }

                    // Check if this is a game start or end event - these are critical for synchronization
                    boolean isGameStart;
                    boolean isGameEnd = false;
                    String gameId;
                    String lobbyId = null;
                    
                    if (message.getGamePayload() != null) {
                        // Get event type
                        String event = message.getGamePayload().getEvent();
                        
                        // Get game ID and lobby ID from Game object if available
                        if (message.getGamePayload().getGame() != null) {
                            Game game = message.getGamePayload().getGame();
                            gameId = game.getGameId();
                            lobbyId = game.getLobbyId();
                            
                            // CRITICAL FIX: Ensure game is properly initialized
                            // This is a backup check in case the WebSocketMessageConverter didn't handle it
                            if (game.getCurrentDrawer() == null && game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                                // If no drawer is set but we have players, select the first player as drawer
                                User firstPlayer = game.getPlayers().get(0);
                                game.setCurrentDrawer(firstPlayer);
                                game.setCurrentDrawerId(firstPlayer.getUserId());
                                Log.d(TAG, "🎨 WebSocketService setting first player as drawer: " + 
                                      firstPlayer.getUsername() + " (" + firstPlayer.getUserId() + ")");
                                
                                // Also set word to guess if missing
                                if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
                                    game.setCurrentWord("apple"); // Default word
                                    Log.d(TAG, "📝 WebSocketService setting default word to guess: 'apple'");
                                }
                            }
                            
                            // Make sure current round is set
                            if (game.getCurrentRound() <= 0) {
                                game.setCurrentRound(1);
                                Log.d(TAG, "🔢 WebSocketService setting current round to 1");
                            }
                            
                            // Make sure total rounds is set
                            if (game.getTotalRounds() <= 0) {
                                game.setTotalRounds(3); // Default to 3 rounds
                                Log.d(TAG, "🔢 WebSocketService setting total rounds to default (3)");
                            }
                            
                            // Initialize player scores if missing
                            if (game.getPlayerScores() == null || game.getPlayerScores().isEmpty()) {
                                Map<String, Float> playerScores = new HashMap<>();
                                // Initialize scores for all players
                                if (game.getPlayers() != null) {
                                    for (User player : game.getPlayers()) {
                                        playerScores.put(player.getUserId(), 0.0f);
                                    }
                                    Log.d(TAG, "💯 WebSocketService initialized scores for " + 
                                          game.getPlayers().size() + " players");
                                }
                                game.setPlayerScores(playerScores);
                            }
                            
                            // Log the game state for debugging
                            Log.d(TAG, "🎮 Game state: ID=" + gameId + 
                                  ", Drawer=" + (game.getCurrentDrawer() != null ? 
                                                game.getCurrentDrawer().getUsername() : "null") +
                                  ", Word=" + game.getCurrentWord() +
                                  ", Round=" + game.getCurrentRound() + "/" + game.getTotalRounds());
                        } else {
                            gameId = null;
                        }

                        // Identify event type
                        isGameStart = event != null && event.equals("started");
                        isGameEnd = event != null && (event.equals("ended") || event.equals("finished"));
                        
                        // CRITICAL FIX: Handle missing game start events by detecting active games for our lobby
                        if (gameId != null && !isGameEnd && currentLobbyId != null && lobbyId != null && 
                            currentLobbyId.equals(lobbyId) && (activeGameId == null || !activeGameId.equals(gameId))) {
                            
                            Log.w(TAG, "⚠️ Detected potential recovery scenario: Game " + gameId + " for our lobby " + lobbyId);
                            
                            // Treat this as a start event to trigger UI transition
                            isGameStart = true;
                            
                            // Track as active game
                            activeGameId = gameId;
                            
                            // If this was previously pending, clear the pending flag
                            if (pendingGameId != null && pendingGameId.equals(gameId)) {
                                Log.d(TAG, "✓ Clearing pendingGameId as we're now handling it");
                                pendingGameId = null;
                            }
                        }
                    } else {
                        gameId = null;
                        isGameStart = false;
                    }

                    if (isGameStart) {
                        Log.i(TAG, "📢 CRITICAL GAME START EVENT received for game " + gameId + ", dispatching to ALL listeners");
                        
                        // For game start events, ensure extra logging and send through ALL available channels
                        Log.d(TAG, "📱 WebSocket callback registration status - General: " + (callback != null ? "registered" : "NULL") + 
                              ", Game: " + (gameUpdateCallback != null ? "registered" : "NULL") +
                              ", Lobby: " + (lobbyUpdateCallback != null ? "registered" : "NULL"));
                    } else if (isGameEnd) {
                        Log.i(TAG, "🎵 GAME END EVENT received for game " + gameId + ", lobby " + lobbyId + ", will reset inGame state");
                        
                        // Reset inGame flag on the lobby when game ends
                        if (lobbyId != null && WebSocketService.this.lobbyRepository != null) {
                            try {
                                Log.d(TAG, "Resetting inGame state for lobby " + lobbyId);
                                WebSocketService.this.lobbyRepository.setLobbyInGameState(lobbyId, false);
                            } catch (Exception e) {
                                Log.e(TAG, "Error resetting lobby inGame state: " + e.getMessage(), e);
                            }
                        } else {
                            Log.w(TAG, "Cannot reset inGame state: " + 
                                  (lobbyId == null ? "Missing lobby ID" : "LobbyRepository not initialized"));
                        }
                    }

                    // Create a flag to track if any callbacks were notified
                    final boolean[] anyCallbacksNotified = {false};
                    
                    // Run notifications on main thread to avoid UI thread issues
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    boolean finalIsGameStart = isGameStart;
                    mainHandler.post(() -> {
                        if (finalIsGameStart && lobbyUpdateCallback != null) {
                            try {
                                Log.d(TAG, "🚨 PRIORITY notification to lobby callback for game state");
                                lobbyUpdateCallback.onGameStateChanged(message);
                                anyCallbacksNotified[0] = true;
                                
                                // If this was a recovery from a missed start_game message,
                                // update tracking variables
                                if (pendingGameId != null && pendingGameId.equals(gameId)) {
                                    pendingGameId = null;
                                    Log.d(TAG, "✅ Successfully handled pending game transition for " + gameId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in lobby callback for game start: " + e.getMessage(), e);
                            }
                        }
                        
                        // Then notify general callback
                        if (callback != null) {
                            try {
                                Log.d(TAG, "Notifying general WebSocket callback of game state event");
                                callback.onGameStateChanged(message);
                                anyCallbacksNotified[0] = true;
                            } catch (Exception e) {
                                Log.e(TAG, "Error in general callback for game state: " + e.getMessage(), e);
                            }
                        }

                        if (gameUpdateCallback != null) {
                            try {
                                Log.d(TAG, "Notifying game update callback of game state event");
                                gameUpdateCallback.onGameStateChanged(message);
                                anyCallbacksNotified[0] = true;
                            } catch (Exception e) {
                                Log.e(TAG, "Error in game callback for game state: " + e.getMessage(), e);
                            }
                        }

                        // For critical game start events, retry if no callbacks were successfully notified
                        // or retry anyway for extra reliability with game start events
                        if (finalIsGameStart) {
                            if (!anyCallbacksNotified[0]) {
                                Log.w(TAG, "⚠️ No callbacks successfully processed game start event! Retrying in 500ms");
                                mainHandler.postDelayed(() -> handleGameStateMessage(json), 500);
                            } else {
                                // Even if callbacks were notified, schedule one retry for reliability
                                Log.d(TAG, "🔄 Scheduling one extra delivery attempt for game start event");
                                mainHandler.postDelayed(() -> {
                                    // Only retry if we're still connected
                                    if (isConnected && lobbyUpdateCallback != null) {
                                        Log.d(TAG, "🔁 Performing extra delivery attempt for game start event");
                                        try {
                                            lobbyUpdateCallback.onGameStateChanged(message);
                                        } catch (Exception ignored) {}
                                    }
                                }, 1000); // Wait 1 second before retry
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing game_state message: " + e.getMessage(), e);
                    notifyError("Error processing game_state message: " + e.getMessage());
                }
            }
            
            /**
             * Process start_game WebSocket messages
             * Converts the start_game message to a GameStateMessage and ensures
             * proper transition to game screen for ALL players
             */
            private void handleStartGameMessage(String json) {
                Log.d(TAG, "Processing start_game message");

                // Convert the start_game message to a GameStateMessage
                GameStateMessage gameStateMessage = messageConverter.createGameStateFromStartGameMessage(json);

                if (gameStateMessage == null) {
                    Log.e(TAG, "Failed to convert start_game message to GameStateMessage");
                    notifyError("Failed to process start game message");
                    return;
                }

                // Extract game ID and game object
                String gameId = "unknown";
                Game game = null;
                if (gameStateMessage.getGamePayload() != null && gameStateMessage.getGamePayload().getGame() != null) {
                    game = gameStateMessage.getGamePayload().getGame();
                    gameId = game.getGameId();
                }

                // If current user is the host, perform duplicate check using pendingGameId
                if (game != null && game.getHostId() != null && currentUserId != null && currentUserId.equals(game.getHostId())) {
                    if (pendingGameId != null && pendingGameId.equals(gameId)) {
                        Log.d(TAG, "Host: Game start already processed for game id " + gameId + ", ignoring duplicate");
                        return;
                    }
                } else {
                    // Non-host: always process the game start message even if duplicate
                    Log.d(TAG, "Non-host processing game start message (duplicate check bypassed)");
                }

                // Store the game id to prevent duplicate processing for hosts
                pendingGameId = gameId;

                // Set game state to ACTIVE and ensure valid round number
                if (game != null) {
                    game.setGameState(Game.GameState.ACTIVE);
                    
                    // Ensure valid round number
                    if (gameStateMessage.getGamePayload().getRoundNumber() <= 0) {
                        Log.w(TAG, "Round number is <= 0, setting default round number to 1");
                        gameStateMessage.getGamePayload().setCurrentRound(1);
                    }
                    
                    // Ensure max rounds is set
                    if (gameStateMessage.getGamePayload().getMaxRounds() <= 0) {
                        Log.w(TAG, "Max rounds is <= 0, setting default max rounds to 3");
                        gameStateMessage.getGamePayload().setMaxRounds(3);
                    }
                    
                    // Ensure time remaining is set
                    if (gameStateMessage.getGamePayload().getTimeRemainingSeconds() <= 0) {
                        Log.w(TAG, "Time remaining is <= 0, setting default time to 60 seconds");
                        gameStateMessage.getGamePayload().setTimeRemainingSeconds(60);
                    }
                    
                    // Ensure current drawer is set
                    if (gameStateMessage.getGamePayload().getCurrentDrawer() == null) {
                        Log.w(TAG, "Current drawer is null, setting host as drawer");
                        // Create a User object directly
                        com.example.drawit_app.model.User hostUser = new com.example.drawit_app.model.User();
                        hostUser.setUserId(game.getHostId());
                        hostUser.setUsername("Host");
                        gameStateMessage.getGamePayload().setCurrentDrawer(hostUser);
                    }
                    
                    // Set word to guess if not already set
                    if (gameStateMessage.getGamePayload().getWordToGuess() == null || 
                        gameStateMessage.getGamePayload().getWordToGuess().isEmpty()) {
                        gameStateMessage.getGamePayload().setWordToGuess("apple"); // Default word
                        Log.w(TAG, "Word to guess is empty, setting default word");
                    }
                    
                    // Log detailed game state for debugging
                    Log.d(TAG, "Game state prepared - GameID: " + gameId + 
                          ", Round: " + gameStateMessage.getGamePayload().getCurrentRound() + 
                          "/" + gameStateMessage.getGamePayload().getMaxRounds() + 
                          ", Time: " + gameStateMessage.getGamePayload().getTimeRemainingSeconds() + 
                          ", Drawer: " + (gameStateMessage.getGamePayload().getCurrentDrawer() != null ? 
                                        gameStateMessage.getGamePayload().getCurrentDrawer().getUserId() : "null"));
                }

                // Callback: Notify game state update for all players
                if (gameUpdateCallback != null) {
                    gameUpdateCallback.onGameStateChanged(gameStateMessage);
                    Log.d(TAG, "Game state update sent to callback");
                } else {
                    Log.w(TAG, "Game update callback is null, cannot propagate game state update");
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
                try {
                    Log.d(TAG, "Received drawing update message: " + json);
                    
                    // Parse the message
                    Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
                    JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
                    Map<String, Object> messageMap = adapter.fromJson(json);
                    
                    if (messageMap != null) {
                        // Extract paths JSON string
                        String pathsJson = null;
                        if (messageMap.containsKey("paths")) {
                            // The paths might be a JSON object or a string
                            Object pathsObj = messageMap.get("paths");
                            if (pathsObj instanceof String) {
                                pathsJson = (String) pathsObj;
                            } else {
                                // Convert the paths object back to JSON string
                                pathsJson = moshi.adapter(Object.class).toJson(pathsObj);
                            }
                        }
                        
                        // Extract game ID
                        String gameId = null;
                        if (messageMap.containsKey("gameId")) {
                            gameId = messageMap.get("gameId").toString();
                        }
                        
                        // Notify game update callback if registered
                        if (gameUpdateCallback != null && pathsJson != null) {
                            // Create a game state message with the drawing paths
                            GameStateMessage gameStateMessage = new GameStateMessage();
                            gameStateMessage.setType("drawing_update");
                            
                            // Set up the game payload
                            GameStateMessage.GamePayload payload = new GameStateMessage.GamePayload();
                            payload.setEvent("drawing_update");
                            payload.setDrawingPaths(pathsJson);
                            
                            // Set the game ID if available
                            if (gameId != null) {
                                Game game = new Game();
                                game.setGameId(gameId);
                                payload.setGame(game);
                            }
                            
                            gameStateMessage.setGamePayload(payload);
                            
                            // Notify the callback on the main thread
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> {
                                gameUpdateCallback.onGameStateChanged(gameStateMessage);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing drawing update message: " + e.getMessage(), e);
                }
            }
            
            // Process error messages from server
            private void handleErrorMessage(String json) {
                try {
                    JSONObject jsonObject = new JSONObject(json);
                    String errorMessage = jsonObject.optString("message", "Unknown server error");
                    Log.e(TAG, "Server error: " + errorMessage);
                    
                    // Notify callbacks about the error
                    notifyError("Server error: " + errorMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing error message: " + e.getMessage());
                    notifyError("Failed to process server error message");
                }
            }

            // Notify all registered callbacks about an error on the main thread
            private void notifyError(String errorMessage) {
                // Always run callbacks on main thread to avoid crashes when showing UI elements like Toast
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    Log.e(TAG, "WebSocket error: " + errorMessage);
                    
                    if (callback != null) {
                        try {
                            callback.onError(errorMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in main callback's error handler: " + e.getMessage(), e);
                        }
                    }

                    if (lobbyUpdateCallback != null) {
                        try {
                            lobbyUpdateCallback.onError(errorMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in lobby callback's error handler: " + e.getMessage(), e);
                        }
                    }

                    if (gameUpdateCallback != null) {
                        try {
                            gameUpdateCallback.onError(errorMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in game callback's error handler: " + e.getMessage(), e);
                        }
                    }
                });
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
        if (webSocket != null) {
            Log.i(TAG, "Closing WebSocket connection");
            webSocket.close(NORMAL_CLOSURE_STATUS, "Closing connection");
            webSocket = null;
        }
        isConnected = false;
        isConnecting = false;
        reconnectEnabled = false; // Don't auto-reconnect after manual disconnection
    }

    /**
     * Manually reconnect the WebSocket connection
     * Forces a reconnection attempt even if already connected
     */
    public void reconnect() {
        Log.i(TAG, "Manual reconnection requested");
        
        // Force disconnect if already connected
        if (webSocket != null) {
            webSocket.close(1000, "Reconnecting");
            webSocket = null;
        }
        
        isConnected = false;
        isConnecting = false;
        reconnectEnabled = true;
        reconnectAttempts = 0;
        
        // Start connection process
        connect();
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
     * Set the LobbyRepository for handling lobby state updates
     * This method is used to break the circular dependency in the DI graph
     * 
     * @param repository The lobby repository instance
     */
    public void setLobbyRepository(LobbyRepository repository) {
        this.lobbyRepository = repository;
        Log.d(TAG, "LobbyRepository has been set via setter");
    }
    
    /**
     * Send a raw message via WebSocket
     *
     * @param message the raw JSON message as a string
     */
    public void sendMessage(String message) {
        if (webSocket != null) {
            try {
                webSocket.send(message);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError("Failed to send message: " + e.getMessage());
                }
            }
        } else {
            Log.e(TAG, "Cannot send message: WebSocket is not connected");
        }
    }
    
    /**
     * Send a game-related message via WebSocket with type and game ID
     * Uses the WebSocketMessageConverter for consistent message formatting
     *
     * @param messageType the type of message (e.g., "round_complete", "correct_guess")
     * @param gameId the ID of the game this message relates to
     */
    public void sendMessage(String messageType, String gameId) {
        if (webSocket != null) {
            try {
                // Create a map with the message data
                Map<String, Object> messageData = new HashMap<>();
                messageData.put("type", messageType);
                messageData.put("game_id", gameId);
                
                // Add timestamp for server validation
                messageData.put("timestamp", System.currentTimeMillis());
                
                // Convert to JSON using Moshi for consistency with the rest of the app
                JsonAdapter<Map<String, Object>> adapter = moshi.adapter(
                    Types.newParameterizedType(Map.class, String.class, Object.class));
                String jsonMessage = adapter.toJson(messageData);
                
                // Log the message being sent
                Log.d(TAG, "📤 Sending WebSocket message: " + messageType + " for game: " + gameId);
                
                // Send the message
                webSocket.send(jsonMessage);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send " + messageType + " message: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError("Failed to send " + messageType + " message: " + e.getMessage());
                }
            }
        } else {
            Log.e(TAG, "Cannot send " + messageType + " message: WebSocket is not connected");
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
     * Set current user ID for context in message handling
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        Log.d(TAG, "Current user id set to: " + userId);
    }
    
    /**
     * Join a lobby via WebSocket to ensure proper connection mapping
     * This registers the current WebSocket connection with a specific lobby ID on the server
     * @param lobbyId The ID of the lobby to join
     * @param userId The ID of the user joining the lobby
     */
    public void joinLobby(String lobbyId, String userId) {
        try {
            if (!isConnected || webSocket == null) {
                Log.e(TAG, "Cannot join lobby: WebSocket is not connected");
                return;
            }
            
            // First update the current lobby ID locally
            setCurrentLobbyId(lobbyId);
            
            // Create the join_lobby message with both lobbyId AND userId
            // This is critical for the server to properly map the connection
            String joinMessage = String.format("{\"type\":\"join_lobby\",\"lobbyId\":\"%s\",\"userId\":\"%s\"}", 
                                              lobbyId, userId);
            
            // Send the join message to the server
            Log.i(TAG, "Sending WebSocket join_lobby message for lobby: " + lobbyId + ", user: " + userId);
            webSocket.send(joinMessage);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send join lobby message: " + e.getMessage(), e);
            if (callback != null) {
                callback.onError("Failed to join lobby via WebSocket: " + e.getMessage());
            }
        }
    }
    
    /**
     * Leave a lobby via WebSocket
     * This method sends a leave_lobby message to the server and handles errors gracefully
     * 
     * @param lobbyId The ID of the lobby to leave
     */
    public void leaveLobby(String lobbyId) {
        // Skip if not connected to WebSocket
        if (webSocket == null) {
            Log.d(TAG, "Cannot leave lobby: WebSocket not connected");
            return;
        }
        
        // Skip if lobby ID is null or empty
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.d(TAG, "Cannot leave lobby: Invalid lobby ID");
            return;
        }
        
        try {
            // Clear current lobby ID if it matches the one we're leaving
            if (currentLobbyId != null && currentLobbyId.equals(lobbyId)) {
                Log.d(TAG, "Clearing current lobby ID: " + lobbyId);
                setCurrentLobbyId(null);
            } else {
                Log.d(TAG, "Not in lobby " + lobbyId + ", current lobby is " + 
                        (currentLobbyId != null ? currentLobbyId : "null"));
            }
            
            // Create and send the leave_lobby message
            String leaveMessage = String.format("{\"type\":\"leave_lobby\",\"lobbyId\":\"%s\"}", lobbyId);
            webSocket.send(leaveMessage);
            Log.d(TAG, "Successfully sent leave_lobby message for lobby: " + lobbyId);
        } catch (Exception e) {
            Log.e(TAG, "Error leaving lobby: " + e.getMessage(), e);
            // Intentionally NOT propagating this error to the UI via callback
            // to avoid annoying error toasts
        }
    }
    
    /**
     * Process lobby_joined message sent by the server when a client successfully joins a lobby
     * This confirms the server has successfully mapped the WebSocket connection to the lobby
     */
    private void handleLobbyJoinedMessage(String json) {
        try {
            Log.i(TAG, "Received lobby_joined confirmation from server");
            
            // Parse basic info from the message
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> mapAdapter = moshi.adapter(mapType);
            Map<String, Object> messageMap = mapAdapter.fromJson(json);
            
            if (messageMap != null && messageMap.containsKey("payload") && messageMap.get("payload") instanceof Map) {
                Map<String, Object> payloadMap = (Map<String, Object>) messageMap.get("payload");
                String lobbyId = null;
                String userId = null;
                
                if (payloadMap.containsKey("lobbyId")) {
                    lobbyId = payloadMap.get("lobbyId").toString();
                    Log.d(TAG, "Joined lobby with ID: " + lobbyId);
                    currentLobbyId = lobbyId;
                }
                
                if (payloadMap.containsKey("userId")) {
                    userId = payloadMap.get("userId").toString();
                    Log.d(TAG, "User with ID: " + userId + " joined lobby");
                }
                
                // Create a lobby state message from this confirmation
                LobbyStateMessage stateMessage = getLobbyStateMessage(lobbyId, userId);

                // Notify callbacks on main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    Log.d(TAG, "Notifying callbacks about successful lobby join");
                    
                    // Notify all registered callbacks
                    if (callback != null) {
                        try {
                            callback.onLobbyStateChanged(stateMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error notifying main callback about lobby joined: " + e.getMessage(), e);
                        }
                    }
                    
                    if (lobbyUpdateCallback != null) {
                        try {
                            lobbyUpdateCallback.onLobbyStateChanged(stateMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error notifying lobby callback about lobby joined: " + e.getMessage(), e);
                        }
                    }
                });
                
            } else {
                Log.w(TAG, "Invalid lobby_joined message format");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing lobby_joined message: " + e.getMessage(), e);
        }
    }

    @NonNull
    private static LobbyStateMessage getLobbyStateMessage(String lobbyId, String userId) {
        LobbyStateMessage stateMessage = new LobbyStateMessage();
        stateMessage.setType("lobby_state");
        LobbyStateMessage.LobbyPayload payload = new LobbyStateMessage.LobbyPayload();

        if (lobbyId != null) {
            Lobby lobby = new Lobby();
            lobby.setLobbyId(lobbyId);
            payload.setLobby(lobby);
        }

        payload.setEvent("joined");
        if (userId != null) {
            payload.setUserId(userId);
        }

        stateMessage.setLobbyPayload(payload);
        return stateMessage;
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