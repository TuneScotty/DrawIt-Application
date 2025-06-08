package com.example.drawit_app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.GameDao;
import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;

import com.example.drawit_app.api.ApiService;
import com.example.drawit_app.api.WebSocketService;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.util.WebSocketMessageConverter;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Repository for game-related operations
 */
@Singleton
public class GameRepository extends BaseRepository {
    @Override
    public void onFailure(retrofit2.Call<com.example.drawit_app.api.response.ApiResponse<com.example.drawit_app.api.response.LobbyListResponse>> call, Throwable t) {
        Log.e("GameRepository", "API call failed: " + t.getMessage(), t);
    }
    
    /**
     * Override handleExpiredToken from BaseRepository
     * Delegates to UserRepository for token refresh and handling
     */
    @Override
    protected <T> void handleExpiredToken(retrofit2.Call<com.example.drawit_app.api.response.ApiResponse<T>> originalCall, MutableLiveData<Resource<T>> result) {
        Log.d("GameRepository", "Token expired in GameRepository, delegating to UserRepository");
        
        // Get the current token (even if expired) from UserRepository
        String currentToken = userRepository.getAuthToken();
        if (currentToken == null) {
            Log.e("GameRepository", "No token available for refresh");
            result.postValue(Resource.error("Not authenticated", null));
            return;
        }
        
        // Use UserRepository to refresh the token
        userRepository.refreshToken(currentToken, refreshSuccess -> {
            if (refreshSuccess) {
                Log.d("GameRepository", "Token refreshed successfully via UserRepository, retrying original request");
                
                try {
                    // Clone the original call
                    retrofit2.Call<com.example.drawit_app.api.response.ApiResponse<T>> newCall =
                            (retrofit2.Call<com.example.drawit_app.api.response.ApiResponse<T>>) originalCall.clone();
                    
                    // Execute the cloned call with the retry flag set to true
                    LiveData<Resource<T>> retryResult = callApi(newCall, true);
                    
                    // Forward the retry result to the original result
                    androidx.lifecycle.Observer<Resource<T>> observer = new androidx.lifecycle.Observer<Resource<T>>() {
                        @Override
                        public void onChanged(Resource<T> resource) {
                            // Update the result with the resource
                            result.postValue(resource);
                            // Remove the observer after first update to prevent memory leaks
                            retryResult.removeObserver(this);
                        }
                    };
                    // Observe the retry result
                    retryResult.observeForever(observer);
                } catch (Exception e) {
                    Log.e("GameRepository", "Error retrying request after token refresh: " + e.getMessage());
                    result.postValue(Resource.error("Error retrying request: " + e.getMessage(), null));
                }
            } else {
                Log.e("GameRepository", "Token refresh failed");
                result.postValue(Resource.error("Session expired, please login again", null));
            }
        });
    }

    
    private final ApiService apiService;
    private final UserRepository userRepository;
    private final GameDao gameDao;
    private final WebSocketService webSocketService;
    private final WebSocketMessageConverter messageConverter;
    
    // Executor for background operations
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    private final MutableLiveData<Game> currentGame = new MutableLiveData<>();
    private final MutableLiveData<Integer> timeRemaining = new MutableLiveData<>();
    
    // Chat messages for the current game
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    
    // WebSocket callback for game state updates
    // Removed redundant gameUpdateCallback field as we're using setupWebSocketCallback() instead
    
    // Current drawing paths JSON
    private final MutableLiveData<String> drawingPaths = new MutableLiveData<>("");
    
    @Inject
    public GameRepository(ApiService apiService, DrawItDatabase database, 
                         UserRepository userRepository, WebSocketService webSocketService,
                         WebSocketMessageConverter messageConverter) {
        this.apiService = apiService;
        this.gameDao = database.gameDao();
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        this.messageConverter = messageConverter;
        
        // Initialize WebSocket callback for game updates
        setupWebSocketCallback();
    }
    
    /**
     * Public method to handle error messages
     * @param message The error message to be displayed
     */
    public void handleError(String message) {
        setError(message);
    }
    
    /**
     * Set up WebSocket callback for real-time game updates
     * This method is used by other parts of the code that don't need specific error handling for game joining
     */
    private void setupWebSocketCallback() {
        webSocketService.setGameUpdateCallback(new WebSocketService.GameUpdateCallback() {
            @Override
            public void onError(String errorMessage) {
                Log.e("GameRepository", "⚠️ WebSocket error: " + errorMessage);
                handleError(errorMessage);
            }
            
            @Override
            public void onGameStateChanged(GameStateMessage message) {
                try {
                    GameStateMessage.GamePayload payload = message.getGamePayload();
                    if (payload == null || payload.getGame() == null) {
                        Log.e("GameRepository", "Invalid game payload received");
                        return;
                    }
                    
                    String event = payload.getEvent();
                    final Game updatedGame = messageConverter.convertToGame(payload.getGame());
                    String gameId = updatedGame.getGameId();
                    
                    Log.d("GameRepository", "Game event received: " + event + " for game " + gameId);
                    
                    // Handle specific game events
                    if (event == null) {
                        handleGenericUpdate(updatedGame, payload);
                    } else if (event.equals("started")) {
                        handleGameStarted(updatedGame, payload);
                    } else if (event.equals("round_started")) {
                        handleRoundStarted(updatedGame, payload);
                    } else if (event.equals("drawing_update")) {
                        handleDrawingUpdate(updatedGame, payload);
                    } else if (event.equals("guess_submitted")) {
                        handleGuessSubmitted(updatedGame, payload);
                    } else if (event.equals("round_ended")) {
                        handleRoundEnded(updatedGame, payload);
                    } else if (event.equals("game_ended")) {
                        handleGameEnded(updatedGame, payload);
                    } else {
                        handleGenericUpdate(updatedGame, payload);
                    }
                    
                    // Move database operations to a background thread
                    executor.execute(() -> {
                        try {
                            // Always update the local database with the latest game state
                            Log.d("GameRepository", " Saving game data to local database: " + gameId);
                            gameDao.insert(updatedGame);
                            Log.d("GameRepository", " Game data saved successfully: " + gameId);
                        } catch (Exception e) {
                            Log.e("GameRepository", " Error saving game data: " + e.getMessage(), e);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e("GameRepository", "Error processing game state: " + e.getMessage(), e);
                }
            }
            
            @Override
            public void onChatMessageReceived(ChatMessage chatMessage) {
                if (chatMessage != null) {
                    Log.d("GameRepository", "Chat message received: " + chatMessage.getMessage());
                    List<ChatMessage> currentMessages = chatMessages.getValue();
                    if (currentMessages == null) {
                        currentMessages = new ArrayList<>();
                    }

                    currentMessages.add(chatMessage);
                    chatMessages.postValue(new ArrayList<>(currentMessages));
                }
            }
        });
    }
    
    /**
     * Set up WebSocket callback for real-time game updates with specific error handling for game join failures
     * @param result The LiveData result to update with error states
     * @param gameId The game ID being joined
     */
    private void setupWebSocketCallbackWithErrorHandling(MutableLiveData<Resource<Game>> result, String gameId) {
        webSocketService.setGameUpdateCallback(new WebSocketService.GameUpdateCallback() {
            @Override
            public void onError(String errorMessage) {
                Log.e("GameRepository", "⚠️ WebSocket error received: " + errorMessage);
                
                // Check for specific error messages related to joining games
                if (errorMessage.contains("Lobby is locked") || errorMessage.contains("Server error: Lobby is locked")) {
                    Log.e("GameRepository", "🔒 Cannot join game - lobby is locked");
                    result.postValue(Resource.error("Cannot join game - lobby is locked", null));
                    
                    // Add a system message to inform the user
                    addSystemChatMessage("Cannot join game - the lobby is locked. The game may have already started.");
                } else if (errorMessage.contains("Game not found") || errorMessage.contains("Server error: Game not found")) {
                    Log.e("GameRepository", "🔍 Game not found: " + gameId);
                    result.postValue(Resource.error("Game not found", null));
                    
                    // Add a system message to inform the user
                    addSystemChatMessage("Game not found. It may have been deleted or never existed.");
                } else {
                    // Handle other errors
                    Log.e("GameRepository", "❌ Error joining game: " + errorMessage);
                    result.postValue(Resource.error("Error joining game: " + errorMessage, null));
                    
                    // Add a system message with the error
                    addSystemChatMessage("Error joining game: " + errorMessage);
                }
            }
            @Override
            public void onGameStateChanged(GameStateMessage message) {
                try {
                    GameStateMessage.GamePayload payload = message.getGamePayload();
                    if (payload == null || payload.getGame() == null) {
                        Log.e("GameRepository", "Invalid game payload received");
                        return;
                    }
                    
                    String event = payload.getEvent();
                    final Game updatedGame = messageConverter.convertToGame(payload.getGame());
                    String gameId = updatedGame.getGameId();
                    
                    Log.d("GameRepository", "Game event received: " + event + " for game " + gameId);
                    
                    // Handle specific game events
                    if (event == null) {
                        handleGenericUpdate(updatedGame, payload);
                    } else if (event.equals("started")) {
                        handleGameStarted(updatedGame, payload);
                    } else if (event.equals("round_started")) {
                        handleRoundStarted(updatedGame, payload);
                    } else if (event.equals("drawing_update")) {
                        handleDrawingUpdate(updatedGame, payload);
                    } else if (event.equals("guess_submitted")) {
                        handleGuessSubmitted(updatedGame, payload);
                    } else if (event.equals("round_ended")) {
                        handleRoundEnded(updatedGame, payload);
                    } else if (event.equals("game_ended")) {
                        handleGameEnded(updatedGame, payload);
                    } else {
                        handleGenericUpdate(updatedGame, payload);
                    }
                    
                    // Move database operations to a background thread
                    executor.execute(() -> {
                        try {
                            // Always update the local database with the latest game state
                            Log.d("GameRepository", " Saving game data to local database: " + gameId);
                            gameDao.insert(updatedGame);
                            Log.d("GameRepository", " Game data saved successfully: " + gameId);
                        } catch (Exception e) {
                            Log.e("GameRepository", " Error saving game data: " + e.getMessage(), e);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e("GameRepository", "Error processing game state: " + e.getMessage(), e);
                }
            }

            @Override
            public void onChatMessageReceived(ChatMessage chatMessage) {
                if (chatMessage == null) {
                    Log.e("GameRepository", "Received null chat message");
                    return;
                }

                Log.d("GameRepository", "📨 Chat message received: " +
                        (chatMessage.getMessage() != null ? chatMessage.getMessage() : "<empty>") +
                        " from " + (chatMessage.getSender() != null ? chatMessage.getSender().getUsername() : "unknown"));

                // Add to the chat messages list
                List<ChatMessage> currentMessages = chatMessages.getValue();
                if (currentMessages == null) {
                    currentMessages = new ArrayList<>();
                }

                // Add the new message
                currentMessages.add(chatMessage);

                // Update the LiveData with the new list
                chatMessages.postValue(new ArrayList<>(currentMessages));
            }
        });
    }
    
    /**
     * Start a new game from the current lobby
     * This initiates the game on the server and broadcasts to all connected clients
     */
    public LiveData<Resource<Game>> startGame(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Game>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        // Send start game request to the server
        LiveData<Resource<Game>> result = callApi(apiService.startGame("Bearer " + token, lobbyId));
        
        // The actual game state will be received via WebSocket
        // This just initiates the process on the server
        observeOnce(result, resource -> {
            if (!resource.isSuccess()) {
                Log.e("GameRepository", "Failed to start game: " + resource.getMessage());
            }
        });
        
        return result;
    }
    
    /**
     * Get details for a specific game
     */
    public LiveData<Resource<Game>> getGameDetails(String gameId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Game>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<Game>> result = callApi(apiService.getGameDetails("Bearer " + token, gameId));
        
        // Observe the result to update local database and current game
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Game game = resource.getData();
                gameDao.insert(game);
                currentGame.setValue(game);
            }
        });
        
        return result;
    }
    
    /**
     * Get the current game the user is in
     */
    public LiveData<Game> getCurrentGame() {
        return currentGame;
    }
    
    /**
     * Get chat messages for the current game
     * @return LiveData containing the list of chat messages
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }
    
    /**
     * Get drawing paths for the current game
     * @return LiveData containing the JSON representation of drawing paths
     */
    public LiveData<String> getDrawingPaths() {
        return drawingPaths;
    }
    
    /**
     * Send a chat message in the current game
     * @param gameId The game ID
     * @param message The message text
     */
    public void sendChatMessage(String gameId, String message) {
        // Send via WebSocket - the server will broadcast to all clients
        webSocketService.sendChatMessage(gameId, message);
        
        // We don't add to local chat list here - the message will come back via WebSocket
        // to ensure consistency across all clients
    }
    
    /**
     * Send a notification to the server that the current user correctly guessed the word
     * @param gameId The ID of the current game
     */
    public void sendCorrectGuess(String gameId) {
        // Send the correct guess to the server which will update scores and notify all clients
        webSocketService.sendCorrectGuess(gameId);
    }
    
    /**
     * Add a system message to the chat
     * @param message The system message to add
     */
    public void addSystemChatMessage(String message) {
        // Create a system message
        ChatMessage chatMessage = new ChatMessage(null, message, ChatMessage.MessageType.SYSTEM_MESSAGE);
        
        // Add to list
        List<ChatMessage> currentMessages = chatMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        currentMessages.add(chatMessage);
        chatMessages.postValue(currentMessages);
    }
    
    /**
     * Advance to the next round when the timer reaches zero
     * @param gameId The ID of the current game
     */
    public void advanceToNextRound(String gameId) {
        webSocketService.sendMessage("round_complete", gameId);
    }
    
    /**
     * Update the drawing path for the current game
     * @param gameId The game ID
     * @param pathsJson JSON representation of the paths
     */
    public void updateDrawingPath(String gameId, String pathsJson) {
        // Send via WebSocket to broadcast to all clients
        webSocketService.updateDrawingPath(gameId, pathsJson);
        
        // We don't update local paths directly - they will come back via WebSocket
        // to ensure consistency across all clients
    }
    
    /**
     * Set the WebSocket callback for game updates
     * @param callback The callback to receive game updates
     */
    public void setGameUpdateCallback(WebSocketService.GameUpdateCallback callback) {
        // This method is kept for backward compatibility
        // We prefer to use our internal setupWebSocketCallback method
        // to ensure consistent handling of game events
        Log.w("GameRepository", "External setGameUpdateCallback called - this may override internal handlers");
        webSocketService.setGameUpdateCallback(callback);
    }
    
    /**
     * Process a game update message from WebSocket
     * @param gameData JSON data for game update
     */
    public void processGameUpdate(String gameData) {
        Log.d("GameRepository", "Game update received");
        
        try {
            // Use the WebSocketMessageConverter to parse the game data
            Game updatedGame = messageConverter.convertGameData(gameData);
            
            if (updatedGame != null) {
                // Process the game update (server should provide drawer)
                ensureGameDataComplete(updatedGame);
                
                // Server should provide the word
                if (updatedGame.getCurrentWord() == null || updatedGame.getCurrentWord().isEmpty()) {
                    Log.d("GameRepository", "Warning: Server did not provide a word for the game");
                }
                
                // Update the current game
                currentGame.postValue(updatedGame);
            }
        } catch (Exception e) {
            Log.e("GameRepository", "Error processing game update: " + e.getMessage(), e);
        }
    }
    
    /**
     * Join a game with the given ID
     * @param gameId The game ID to join
     * @return LiveData with game resource
     */
    public LiveData<Resource<Game>> joinGame(String gameId) {
        MutableLiveData<Resource<Game>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        String token = userRepository.getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        Log.i("GameRepository", "🎮 Joining game: " + gameId);
        
        // Register for game updates via WebSocket
        if (webSocketService != null) {
            // Set up callback to receive game state updates with error handling for locked lobbies
            setupWebSocketCallbackWithErrorHandling(result, gameId);
            Log.i("GameRepository", "✅ Registered game update callback for game: " + gameId);
            
            // Set the active game ID in WebSocketService to track this game
            webSocketService.setActiveGameId(gameId);
            Log.d("GameRepository", "🔄 Set active game ID in WebSocketService: " + gameId);
            
            // Use executor for database operations
            executor.execute(() -> {
                try {
                    // Check if game exists in local database
                    Game localGame = gameDao.getGameByIdSync(gameId);
                    
                    if (localGame != null) {
                        Log.d("GameRepository", "📋 Found game in local database: " + gameId);
                        currentGame.postValue(localGame);
                        result.postValue(Resource.success(localGame));
                    } else {
                        // We don't have the game locally yet
                        Log.d("GameRepository", "🔍 Game not found in local database, waiting for WebSocket updates");
                        
                        // Post on main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            // Return success with null data - the actual game data will come via WebSocket
                            result.postValue(Resource.success(null));
                        });
                    }
                } catch (Exception e) {
                    Log.e("GameRepository", "⚠️ Error accessing local database: " + e.getMessage(), e);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        result.postValue(Resource.success(null)); // Still continue with WebSocket
                    });
                }
            });
            
            // Explicitly request a game state update via WebSocket
            sendGameStateRequest(gameId);
            
            // Set up a retry mechanism with multiple attempts
            setupGameStateRetryMechanism(gameId, 3); // Use 3 as the maximum number of retries
            
        } else {
            Log.e("GameRepository", "❌ WebSocketService is null, cannot register for game updates");
            result.postValue(Resource.error("WebSocket service unavailable", null));
        }
        
        return result;
    }
    
    /**
     * Send a game state request via WebSocket
     * @param gameId The game ID to request state for
     */
    private void sendGameStateRequest(String gameId) {
        try {
            // Create and send a game_state_request message
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("type", "game_state_request");
            requestData.put("game_id", gameId);
            requestData.put("timestamp", System.currentTimeMillis());
            
            // Convert to JSON and send via WebSocket
            Moshi moshi = new Moshi.Builder().build();
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(
                Types.newParameterizedType(Map.class, String.class, Object.class));
            String jsonRequest = adapter.toJson(requestData);
            
            webSocketService.sendMessage(jsonRequest);
            Log.d("GameRepository", "📤 Sent explicit game state request for: " + gameId);
        } catch (Exception e) {
            Log.e("GameRepository", "⚠️ Failed to send game state request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Set up a retry mechanism for game state requests
     *
     * @param gameId The game ID to request state for
     * @param maxRetries Maximum number of retries
     */
    private void setupGameStateRetryMechanism(String gameId, int maxRetries) {
        // Create a handler for the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        
        // Schedule the first retry check
        handler.postDelayed(() -> checkAndRetryGameStateRequest(gameId, 1, maxRetries, handler), 3000);
    }
    
    /**
     * Check if game data is received and retry if needed
     * @param gameId The game ID
     * @param currentRetry Current retry count
     * @param maxRetries Maximum retries allowed
     * @param handler Handler for scheduling next retry
     */
    private void checkAndRetryGameStateRequest(String gameId, int currentRetry, int maxRetries, Handler handler) {
        // Check if we've received the game data yet
        if (currentGame.getValue() == null) {
            Log.w("GameRepository", "⚠️ Still waiting for game data after timeout, retry #" + currentRetry);
            
            // Send another request
            sendGameStateRequest(gameId);
            
            // Schedule another retry if we haven't reached the maximum
            if (currentRetry < maxRetries) {
                handler.postDelayed(() -> 
                    checkAndRetryGameStateRequest(gameId, currentRetry + 1, maxRetries, handler), 
                    3000); // 3 second delay between retries
            } else {
                Log.e("GameRepository", "❌ Maximum retries reached for game state request: " + gameId);
            }
        } else {
            Log.d("GameRepository", "✅ Game data received successfully after " + (currentRetry - 1) + " retries");
        }
    }
    
    /**
     * Get the time remaining in the current round
     */
    public LiveData<Integer> getTimeRemaining() {
        return timeRemaining;
    }
    
    private void handleGameStarted(Game game, GameStateMessage.GamePayload payload) {
        if (payload.getTimeRemainingSeconds() > 0) {
            timeRemaining.postValue(payload.getTimeRemainingSeconds());
        } else if (game.getRoundDurationSeconds() > 0) {
            timeRemaining.postValue(game.getRoundDurationSeconds());
        }
        
        // Server should provide the word
        if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
            Log.d("GameRepository", "Warning: Server did not provide a word for the game");
        }
        
        // Add system message
        String drawerName = game.getCurrentDrawer() != null ? 
                game.getCurrentDrawer().getUsername() : "Unknown player";
        addSystemChatMessage("Round " + game.getCurrentRound() + 
                " started! " + drawerName + " is drawing.");
        
        // Update current game
        currentGame.postValue(game);
    }

    /**
     * Handle round started event
     */
    private void handleRoundStarted(Game game, GameStateMessage.GamePayload payload) {
        if (payload.getTimeRemainingSeconds() > 0) {
            timeRemaining.postValue(payload.getTimeRemainingSeconds());
        } else if (game.getRoundDurationSeconds() > 0) {
            timeRemaining.postValue(game.getRoundDurationSeconds());
        }
        
        // Server should provide the word
        if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
            Log.d("GameRepository", "Warning: Server did not provide a word for round start");
        }
        
        // Add system message
        String drawerName = game.getCurrentDrawer() != null ? 
                game.getCurrentDrawer().getUsername() : "Unknown player";
        addSystemChatMessage("Round " + game.getCurrentRound() + 
                " started! " + drawerName + " is drawing.");
        
        // Update current game
        currentGame.postValue(game);
    }
    
    /**
     * Handle drawing update event
     */
    private void handleDrawingUpdate(Game game, GameStateMessage.GamePayload payload) {
        if (payload.getDrawingPaths() != null) {
            drawingPaths.postValue(payload.getDrawingPaths());
            Log.d("GameRepository", "Updated drawing paths");
        }
    }
    
    /**
     * Handle guess submitted event
     */
    private void handleGuessSubmitted(Game game, GameStateMessage.GamePayload payload) {
        // Add system message about the correct guess
        String message = null;
        
        // Try to extract message from event field
        if (payload.getEvent() != null && payload.getEvent().contains(":")) {
            message = payload.getEvent().split(":", 2)[1].trim();
        }
        
        if (message != null && !message.isEmpty()) {
            addSystemChatMessage(message);
        } else {
            // Default message if none provided
            addSystemChatMessage("A player made a correct guess!");
        }
        
        // Update current game
        currentGame.postValue(game);
    }
    
    /**
     * Handle round ended event
     */
    private void handleRoundEnded(Game game, GameStateMessage.GamePayload payload) {
        Log.i("GameRepository", "Round ended event for game: " + game.getGameId() + 
           ", round: " + game.getCurrentRound());
        
        // Add system message
        addSystemChatMessage("Round " + game.getCurrentRound() + " ended!");
        
        // The server should handle round progression and send updated game state
        // We'll just log the current state and wait for the server's next update
        Log.d("GameRepository", "Waiting for server to update game state for next round");
        
        if (payload.getGame() != null && payload.getGame().getGameState() != null) {
            // Use the game state from the server payload if available
            game.setGameState(payload.getGame().getGameState());
        }
        
        // If this is the last round, mark the game as finished
        if (game.getCurrentRound() >= game.getTotalRounds()) {
            Log.d("GameRepository", "Final round completed, game should be finished");
            game.setGameState(Game.GameState.FINISHED);
            addSystemChatMessage("Game ended!");
        }
        
        // Update current game with whatever state we have
        // The server will send a new game state message with the next round info
        currentGame.postValue(game);
    }
    
    /**
     * Handle game ended event
     */
    private void handleGameEnded(Game game, GameStateMessage.GamePayload payload) {
        Log.i("GameRepository", "Game ended event for game: " + game.getGameId());
        
        // Update game state
        game.setGameState(Game.GameState.FINISHED);
        
        // Add system message
        addSystemChatMessage("Game ended! Thanks for playing!");
        
        // Update current game
        currentGame.postValue(game);
    }
    
    /**
     * Handle generic game update event
     */
    private void handleGenericUpdate(Game game, GameStateMessage.GamePayload payload) {
        Log.d("GameRepository", "Generic game update for game: " + game.getGameId());
        
        // Update time remaining if available
        if (payload.getTimeRemainingSeconds() > 0) {
            timeRemaining.postValue(payload.getTimeRemainingSeconds());
        }
        
        // Update current game
        currentGame.postValue(game);
    }
    
    /**
     * Helper method to observe a LiveData object once
     */
    private <T> void observeOnce(LiveData<T> liveData, OnObservedListener<T> listener) {
        liveData.observeForever(new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T t) {
                listener.onObserved(t);
                liveData.removeObserver(this);
            }
        });
    }
    
    /**
     * Interface for observing LiveData once
     */
    private interface OnObservedListener<T> {
        void onObserved(T t);
    }
    
    /**
     * Log game state information
     * 
     * @param game The game to process
     */
    private void ensureGameDataComplete(Game game) {
        // Server should provide both drawer and word
        // Just log current game state for debugging
        Log.d("GameRepository", "Processed game update for game: " + game.getGameId() + 
               ", round: " + game.getCurrentRound() + 
               ", drawer: " + (game.getCurrentDrawer() != null ? game.getCurrentDrawer().getUsername() : "null") + 
               ", word: " + (game.getCurrentWord() != null ? game.getCurrentWord() : "null"));
    }
    
    // Removed selectRandomWord method as the server now handles word selection
    
    /**
     * Submit a drawing to the server
     * 
     * @param drawing The drawing to submit
     */
    public void submitDrawing(Drawing drawing) {
        Game game = currentGame.getValue();
        if (game == null) {
            Log.e("GameRepository", "Cannot submit drawing: no active game");
            return;
        }
        
        try {
            // Create a JSON message to send to the server
            JSONObject message = new JSONObject();
            message.put("type", "submit_drawing");
            message.put("gameId", game.getGameId());
            message.put("userId", drawing.getUserId());
            message.put("drawingData", drawing.getPaths());

            // Send the message via WebSocket
            webSocketService.sendMessage(message.toString());
            Log.d("GameRepository", "Drawing submitted for game: " + game.getGameId());
        } catch (JSONException e) {
            Log.e("GameRepository", "Error creating drawing submission message", e);
        }
    }
    
    /**
     * Rate a drawing in the current game
     * 
     * @param drawingId The ID of the drawing to rate
     * @param rating The rating to give (0-5)
     */
    public void rateDrawing(String drawingId, float rating) {
        Game game = currentGame.getValue();
        if (game == null) {
            Log.e("GameRepository", "Cannot rate drawing: no active game");
            return;
        }
        
        try {
            // Create a JSON message to send to the server
            JSONObject message = new JSONObject();
            message.put("type", "rate_drawing");
            message.put("gameId", game.getGameId());
            message.put("drawingId", drawingId);
            message.put("rating", rating);
            
            // Send the message via WebSocket
            webSocketService.sendMessage(message.toString());
            Log.d("GameRepository", "Drawing rated: " + drawingId + ", rating: " + rating);
        } catch (JSONException e) {
            Log.e("GameRepository", "Error creating drawing rating message", e);
        }
    }
}
