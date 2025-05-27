package com.example.drawit_app.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.GameDao;
import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.GameStateMessage;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Repository for game-related operations
 */
@Singleton
public class GameRepository extends BaseRepository {
    
    private final ApiService apiService;
    private final GameDao gameDao;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    
    private final MutableLiveData<Game> currentGame = new MutableLiveData<>();
    private final MutableLiveData<Integer> timeRemaining = new MutableLiveData<>();
    
    // Chat messages for the current game
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    
    // Current drawing paths JSON
    private final MutableLiveData<String> drawingPaths = new MutableLiveData<>("");
    
    @Inject
    public GameRepository(ApiService apiService, DrawItDatabase database, 
                         UserRepository userRepository, WebSocketService webSocketService) {
        this.apiService = apiService;
        this.gameDao = database.gameDao();
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        
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
                GameStateMessage.GamePayload payload = message.getGamePayload();
                if (payload != null && payload.getGame() != null) {
                    Game updatedGame = payload.getGame();
                    
                    // Update current game if it's the one we're in
                    if (currentGame.getValue() != null && 
                        currentGame.getValue().getGameId().equals(updatedGame.getGameId())) {
                        
                        // If game has drawings, set them on the current game
                        if (payload.getDrawings() != null) {
                            updatedGame.setCurrentRoundDrawings(payload.getDrawings());
                        }
                        
                        // If game has player scores, set them on the current game
                        if (payload.getPlayerScores() != null) {
                            updatedGame.setPlayerScores(payload.getPlayerScores());
                        }
                        
                        currentGame.postValue(updatedGame);
                        
                        // Update time remaining
                        timeRemaining.postValue(payload.getTimeRemainingSeconds());
                    }
                    
                    // Update in local database
                    gameDao.insert(updatedGame);
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
        // In a real implementation, this would be sent via WebSocket
        // For now, we'll just add it to the local list
        
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
        
        // In a real implementation, this would be sent to the server
        // webSocketService.sendChatMessage(gameId, message);
    }
    
    /**
     * Update the drawing path for the current game
     * @param gameId The game ID
     * @param pathsJson JSON representation of the paths
     */
    public void updateDrawingPath(String gameId, String pathsJson) {
        // Update local drawing paths
        drawingPaths.postValue(pathsJson);
        
        // In a real implementation, this would be sent to the server
        // webSocketService.updateDrawingPath(gameId, pathsJson);
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
        
        // In a real implementation, this would call an API
        // For now, we'll simulate a successful response
        Game game = new Game();
        game.setGameId(gameId);
        game.setRoundDurationSeconds(60);
        game.setNumRounds(3);
        game.setGameState(Game.GameState.WAITING);
        
        // Update current game
        currentGame.postValue(game);
        
        // Return success
        result.setValue(Resource.success(game));
        
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
