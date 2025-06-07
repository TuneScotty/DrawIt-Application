package com.example.drawit_app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.GameDao;
import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.User;
import com.example.drawit_app.api.ApiService;
import com.example.drawit_app.api.WebSocketService;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.util.WebSocketMessageConverter;

import java.util.ArrayList;
import java.util.List;

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
    
    private final MutableLiveData<Game> currentGame = new MutableLiveData<>();
    private final MutableLiveData<Integer> timeRemaining = new MutableLiveData<>();
    
    // Chat messages for the current game
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    
    // WebSocket callback for game state updates
    private final WebSocketService.GameUpdateCallback gameUpdateCallback = new WebSocketService.GameUpdateCallback() {
        @Override
        public void onGameStateChanged(GameStateMessage message) {
            if (message == null || message.getGamePayload().getGame() == null) {
                Log.e("GameRepository", "Received null game state message or game");
                return;
            }
            
            Game updatedGame = message.getGamePayload().getGame();
            String gameId = updatedGame.getGameId();
            
            // Check if this is a game start event
            boolean isGameStart = message.getType().equals("start_game");
            
            Log.d("GameRepository", "📥 Game state update received: " + message.getType() + 
                  " for game " + gameId);
            
            // CRITICAL FIX: Ensure the current drawer is set
            if (updatedGame.getCurrentDrawer() == null && updatedGame.getPlayers() != null && 
                !updatedGame.getPlayers().isEmpty()) {
                User firstPlayer = updatedGame.getPlayers().get(0);
                updatedGame.setCurrentDrawer(firstPlayer);
                updatedGame.setCurrentDrawerId(firstPlayer.getUserId());
                Log.d("GameRepository", "✅ GameRepository set first player as drawer: " + 
                      firstPlayer.getUsername() + " (ID: " + firstPlayer.getUserId() + ")");
            }
            
            // CRITICAL FIX: Ensure the current word is set
            if (updatedGame.getCurrentWord() == null || updatedGame.getCurrentWord().isEmpty()) {
                updatedGame.setCurrentWord("apple");
                Log.d("GameRepository", "📝 GameRepository set default word to 'apple'");
            }
            
            // CRITICAL FIX: Ensure game state is set
            if (updatedGame.getGameState() == null) {
                updatedGame.setGameState(Game.GameState.ACTIVE);
                Log.d("GameRepository", "🎮 GameRepository set default game state to ACTIVE");
            }
            
            // Update time remaining
            if (message.getGamePayload().getTimeRemainingSeconds() > 0) {
                updatedGame.setRemainingTime(message.getGamePayload().getTimeRemainingSeconds());
            } else if (updatedGame.getRemainingTime() <= 0) {
                // Set a default time if none is provided
                updatedGame.setRemainingTime(60); // Default 60 seconds
                Log.d("GameRepository", "⏱ GameRepository set default remaining time to 60 seconds");
            }
            
            // Log detailed game state for debugging
            Log.d("GameRepository", "📊 Game state details in repository:" +
                  "\n - Current drawer: " + (updatedGame.getCurrentDrawer() != null ? 
                                          updatedGame.getCurrentDrawer().getUsername() : "NULL") +
                  "\n - Current word: " + (updatedGame.getCurrentWord() != null ? 
                                       updatedGame.getCurrentWord() : "NULL") +
                  "\n - Game state: " + (updatedGame.getGameState() != null ? 
                                      updatedGame.getGameState().name() : "NULL") +
                  "\n - Round: " + updatedGame.getCurrentRound() + "/" + updatedGame.getTotalRounds() +
                  "\n - Players: " + (updatedGame.getPlayers() != null ? 
                                   updatedGame.getPlayers().size() : "NULL"));
            
            // CRITICAL FIX: Always update the current game LiveData for game start events
            // or if we're already tracking this game, or if we don't have any game yet
            Game currentGameValue = currentGame.getValue();
            if (isGameStart || currentGameValue == null || 
                (currentGameValue != null && currentGameValue.getGameId().equals(gameId))) {
                Log.d("GameRepository", "💬 Posting updated game to LiveData");
                currentGame.postValue(updatedGame);
            }
        }

        
        @Override
        public void onError(String errorMessage) {
            Log.e("GameRepository", "WebSocket game update error: " + errorMessage);
        }
    };
    
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
     */
    private void setupWebSocketCallback() {
        webSocketService.setGameUpdateCallback(new WebSocketService.GameUpdateCallback() {
            @Override
            public void onGameStateChanged(GameStateMessage message) {
                try {
                    Log.i("GameRepository", "🎮 Received game state message: " + 
                                     (message.getGamePayload() != null && message.getGamePayload().getEvent() != null ? 
                                      message.getGamePayload().getEvent() : "update"));
                    
                    GameStateMessage.GamePayload payload = message.getGamePayload();
                    if (payload != null && payload.getGame() != null) {
                        // Get event type and game ID for logging
                        String event = payload.getEvent();
                        String gameId = payload.getGame().getGameId();
                        
                        // Log detailed message info
                        Log.i("GameRepository", "🎲 Game event details - ID: " + gameId + 
                                         ", Event: " + (event != null ? event : "update") +
                                         ", Current game null? " + (currentGame.getValue() == null));
                        
                        // Use messageConverter to safely convert game object
                        Game updatedGame = messageConverter.convertToGame(payload.getGame());
                        if (updatedGame == null) {
                            Log.e("GameRepository", "❌ Failed to convert game object");
                            return;
                        }
                        
                        // CRITICAL FIX: Special handling for game start events
                        // This allows non-host players to initialize their game state when game starts
                        boolean isGameStart = event != null && event.equals("started");
                        
                        // CRITICAL FIX: Ensure current drawer is set
                        if (updatedGame.getCurrentDrawer() == null && updatedGame.getPlayers() != null && 
                            !updatedGame.getPlayers().isEmpty()) {
                            User firstPlayer = updatedGame.getPlayers().get(0);
                            updatedGame.setCurrentDrawer(firstPlayer);
                            updatedGame.setCurrentDrawerId(firstPlayer.getUserId());
                            Log.d("GameRepository", "🎨 Setting first player as drawer: " + 
                                             firstPlayer.getUsername() + " (" + firstPlayer.getUserId() + ")");
                        }
                        
                        // CRITICAL FIX: Ensure current word is set
                        if (updatedGame.getCurrentWord() == null || updatedGame.getCurrentWord().isEmpty()) {
                            updatedGame.setCurrentWord("apple"); // Default word as fallback
                            Log.d("GameRepository", "📝 Setting default word to 'apple'");
                        }
                        
                        // CRITICAL FIX: Make sure game state is ACTIVE
                        if (updatedGame.getGameState() == null) {
                            updatedGame.setGameState(Game.GameState.ACTIVE);
                            Log.d("GameRepository", "🎮 Setting game state to ACTIVE");
                        }
                        
                        // Check if we should update the current game:
                        // 1. If this is a game start event (for any player, host or non-host)
                        // 2. OR if we already have this game loaded
                        // 3. OR if we don't have any game loaded yet
                        if (isGameStart ||
                            (currentGame.getValue() != null &&
                             currentGame.getValue().getGameId().equals(updatedGame.getGameId())) ||
                            currentGame.getValue() == null) {

                            Log.i("GameRepository", "✅ Updating game state for game " + gameId + 
                                             (isGameStart ? " (GAME START EVENT)" : " (regular update)"));
                            
                            // If game has drawings, safely set them on the current game
                            if (payload.getDrawings() != null) {
                                updatedGame.setCurrentRoundDrawings(payload.getDrawings());
                            }

                            // If game has player scores, set them on the current game
                            if (payload.getPlayerScores() != null) {
                                updatedGame.setPlayerScores(payload.getPlayerScores());
                            }
                            
                            // Handle drawing path updates
                            if ("drawing_update".equals(event) && payload.getDrawingPaths() != null) {
                                Log.d(TAG, "Received drawing path update via WebSocket");
                                drawingPaths.postValue(payload.getDrawingPaths());
                            }

                            // CRITICAL FIX: Update time remaining
                            if (payload.getTimeRemainingSeconds() > 0) {
                                timeRemaining.postValue(payload.getTimeRemainingSeconds());
                                Log.d("GameRepository", "⏱️ Updated time remaining: " + 
                                                 payload.getTimeRemainingSeconds() + " seconds");
                            } else if (updatedGame.getRoundDurationSeconds() > 0) {
                                // If server didn't provide time, use round duration as fallback
                                timeRemaining.postValue(updatedGame.getRoundDurationSeconds());
                                Log.d("GameRepository", "⏱️ Using round duration as time: " + 
                                                 updatedGame.getRoundDurationSeconds() + " seconds");
                            } else {
                                // Last resort fallback
                                timeRemaining.postValue(60); // Default to 60 seconds
                                Log.d("GameRepository", "⏱️ Using default time: 60 seconds");
                            }

                            // Use postValue for thread safety
                            currentGame.postValue(updatedGame);
                            
                            // Log successful update
                            Log.i("GameRepository", "🎯 Game state updated successfully: " + 
                                             "ID=" + gameId + 
                                             ", Drawer=" + (updatedGame.getCurrentDrawer() != null ? 
                                                          updatedGame.getCurrentDrawer().getUsername() : "null") +
                                             ", Word=" + updatedGame.getCurrentWord() +
                                             ", Round=" + updatedGame.getCurrentRound() + "/" + updatedGame.getTotalRounds() +
                                             ", Time=" + timeRemaining.getValue());
                        }

                        // Update in local database
                        gameDao.insert(updatedGame);
                    }
                } catch (Exception e) {
                    Log.e("GameRepository", "Error processing game state: " + e.getMessage(), e);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Handle WebSocket error
                setupWebSocketCallback();
            }
        });
    }
    
    /**
     * Start a new game from the current lobby
     */
    public LiveData<Resource<Game>> startGame(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Game>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<Game>> result = callApi(apiService.startGame("Bearer " + token, lobbyId));
        
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
     * Submit a drawing for the current game and round
     */
    public void submitDrawing(com.example.drawit_app.model.Drawing drawing) {
        String token = userRepository.getAuthToken();
        if (token == null || currentGame.getValue() == null) {
            return;
        }
        
        // Set game and round information on the drawing
        Game game = currentGame.getValue();
        drawing.setGameId(game.getGameId());
        drawing.setRoundNumber(game.getCurrentRound());
        drawing.setWord(game.getCurrentWord());
        
        // Send via WebSocket for real-time updates
        webSocketService.sendDrawing(game.getGameId(), drawing);
        
        // Also send via REST API for persistence
        apiService.submitDrawing("Bearer " + token, game.getGameId(), drawing);
    }
    
    /**
     * Rate a drawing in the current game
     */
    public void rateDrawing(String drawingId, float rating) {
        String token = userRepository.getAuthToken();
        if (token == null || currentGame.getValue() == null) {
            return;
        }
        
        Game game = currentGame.getValue();
        
        // Send via WebSocket for real-time updates
        webSocketService.sendRating(game.getGameId(), drawingId, rating);
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
        // Get current user
        User currentUser = userRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            return;
        }
        
        // Create message
        ChatMessage chatMessage = new ChatMessage(currentUser, message, ChatMessage.MessageType.PLAYER_MESSAGE);
        
        // Add to list
        List<ChatMessage> currentMessages = chatMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        currentMessages.add(chatMessage);
        chatMessages.postValue(currentMessages);
        
        // Send chat message via WebSocket
        if (webSocketService != null) {
            try {
                // Create a message with type "chat_message" and the message content
                String chatJson = String.format("{\"type\":\"chat_message\",\"gameId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"message\":\"%s\"}",
                        gameId, currentUser.getUserId(), currentUser.getUsername(), message);
                webSocketService.sendMessage(chatJson);
                Log.d(TAG, "Sent chat message via WebSocket");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send chat message: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Send a notification to the server that the current user correctly guessed the word
     * @param gameId The ID of the current game
     */
    public void sendCorrectGuess(String gameId) {
        // Get current user
        User currentUser = userRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            return;
        }
        
        // Create a system message to indicate correct guess
        String systemMessage = currentUser.getUsername() + " guessed the word correctly!"; 
        ChatMessage chatMessage = new ChatMessage(null, systemMessage, ChatMessage.MessageType.SYSTEM_MESSAGE);
        
        // Add to list
        List<ChatMessage> currentMessages = chatMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        currentMessages.add(chatMessage);
        chatMessages.postValue(currentMessages);
        
        // Send to server via WebSocket
        webSocketService.sendMessage("correct_guess", gameId);
        
        // Log the action
        Log.d("GameRepository", "✅ User " + currentUser.getUsername() + " correctly guessed the word");
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
        
        // Log the message
        Log.d("GameRepository", "💬 System message: " + message);
    }
    
    /**
     * Advance to the next round when the timer reaches zero
     * @param gameId The ID of the current game
     */
    public void advanceToNextRound(String gameId) {
        // Get current game state
        Game currentGame = getCurrentGame().getValue();
        if (currentGame == null) {
            Log.e("GameRepository", "Cannot advance round - game is null");
            return;
        }
        
        // Log the action
        int currentRound = currentGame.getCurrentRound();
        int maxRounds = currentGame.getNumRounds();
        Log.d("GameRepository", "⏱️ Advancing from round " + currentRound + "/" + maxRounds);
        
        // Send round completion message to server via WebSocket
        webSocketService.sendMessage("round_complete", gameId);
        
        // Add system message about round completion
        addSystemChatMessage("Round " + currentRound + " completed!");
    }
    
    /**
     * Update the drawing path for the current game
     * @param gameId The game ID
     * @param pathsJson JSON representation of the paths
     */
    public void updateDrawingPath(String gameId, String pathsJson) {
        // Update local LiveData
        drawingPaths.postValue(pathsJson);
        
        // Send drawing path update via WebSocket
        if (webSocketService != null && pathsJson != null && !pathsJson.isEmpty()) {
            try {
                // Create a message with type "drawing_update" and the drawing paths
                String message = String.format("{\"type\":\"drawing_update\",\"gameId\":\"%s\",\"paths\":%s}", 
                                              gameId, pathsJson);
                webSocketService.sendMessage(message);
                Log.d(TAG, "Sent drawing path update via WebSocket");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send drawing path update: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Set the WebSocket callback for game updates
     * @param callback The callback to receive game updates
     */
    public void setGameUpdateCallback(WebSocketService.GameUpdateCallback callback) {
        webSocketService.setGameUpdateCallback(callback);
    }
    
    /**
     * Process a game update message from WebSocket
     * @param gameData JSON data for game update
     */
    public void processGameUpdate(String gameData) {
        // This would typically parse the data and handle it
        // For now, we'll just log it
        setError("Received game update: " + (gameData != null ? gameData.substring(0, Math.min(20, gameData.length())) + "..." : "null"));
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
        
        // Register for game updates via WebSocket
        if (webSocketService != null) {
            webSocketService.setGameUpdateCallback(gameUpdateCallback);
            Log.i("GameRepository", "Registered game update callback for game: " + gameId);
        } else {
            Log.e("GameRepository", "WebSocketService is null, cannot register for game updates");
        }
        
        // IMPORTANT: The server doesn't have a /games/{gameId} endpoint implemented yet
        // Instead of using the API, we'll create a temporary game object and rely on WebSocket updates
        // for the actual game state
        Log.i("GameRepository", "Creating temporary game object for: " + gameId);
        
        // Create a temporary game object
        Game game = new Game();
        game.setGameId(gameId);
        game.setRoundDurationSeconds(60);
        game.setNumRounds(3);
        game.setGameState(Game.GameState.ACTIVE);
        game.setCurrentRound(1);
        
        // Create a placeholder drawer to avoid background thread issues
        User drawer = new User();
        drawer.setUserId("current_user");
        drawer.setUsername("You");
        game.setCurrentDrawer(drawer);
        
        // Update current game
        currentGame.postValue(game);
        
        // Return success with temporary data
        // The actual game state will be updated via WebSocket
        result.postValue(Resource.success(game));
        Log.i("GameRepository", "Created temporary game object, waiting for WebSocket updates");
        
        return result;
    }
    
    /**
     * Get the time remaining in the current round
     */
    public LiveData<Integer> getTimeRemaining() {
        return timeRemaining;
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
}
