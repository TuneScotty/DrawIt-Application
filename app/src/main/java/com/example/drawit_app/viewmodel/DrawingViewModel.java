package com.example.drawit_app.viewmodel;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.lifecycle.LiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.User;
import com.example.drawit_app.api.WebSocketService;
import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.DrawingRepository;
import com.example.drawit_app.repository.GameRepository;
import com.example.drawit_app.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for drawing-related operations and drawing archive
 */
@HiltViewModel
public class DrawingViewModel extends ViewModel {
    private static final String TAG = "DrawingViewModel";

    private final DrawingRepository drawingRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    // State of user drawings (archive)
    private final MediatorLiveData<DrawingsState> drawingsState = new MediatorLiveData<>();

    // Current drawing being created/edited
    private final MediatorLiveData<Drawing> currentDrawing = new MediatorLiveData<>();

    // Game-related LiveData
    private final MutableLiveData<Game> currentGame = new MutableLiveData<>();
    private final MutableLiveData<List<String>> chatMessages = new MutableLiveData<>();
    private final MutableLiveData<List<ChatMessage>> chatMessageObjects = new MutableLiveData<>();
    private final MutableLiveData<String> drawingPaths = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gameOverEvent = new MutableLiveData<>();
    private final MutableLiveData<Drawing> drawingDetails = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> _error = new MutableLiveData<>();

    /**
     * Get error messages
     */
    public LiveData<String> getError() {
        return _error;
    }

    @Inject
    public DrawingViewModel(DrawingRepository drawingRepository, UserRepository userRepository, GameRepository gameRepository) {
        this.drawingRepository = drawingRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;

        // Initialize states
        drawingsState.setValue(new DrawingsState(null, null, false));
        isLoading.setValue(false);
        chatMessages.setValue(java.util.Collections.emptyList());
        chatMessageObjects.setValue(new ArrayList<>());
    }

    /**
     * Load the user's drawing archive
     */
    public void loadUserDrawings() {
        // Set loading state
        drawingsState.setValue(new DrawingsState(null, null, true));

        // Call repository to get user drawings
        LiveData<Resource<List<Drawing>>> result = drawingRepository.getUserDrawings();
        drawingsState.addSource(result, resource -> {
            if (resource.isLoading()) {
                // Already set loading state above
            } else if (resource.isSuccess()) {
                drawingsState.setValue(new DrawingsState(resource.getData(), null, false));
            } else if (resource.isError()) {
                drawingsState.setValue(new DrawingsState(null, resource.getMessage(), false));
            }

            drawingsState.removeSource(result);
        });
    }

    /**
     * Search drawings by word
     */
    public void searchDrawings(String word) {
        // Set loading state
        drawingsState.setValue(new DrawingsState(drawingsState.getValue().getDrawings(), null, true));

        // Call repository to search drawings
        LiveData<Resource<List<Drawing>>> result = drawingRepository.searchDrawings(word);
        drawingsState.addSource(result, resource -> {
            if (resource.isLoading()) {
                // Already set loading state above
            } else if (resource.isSuccess()) {
                drawingsState.setValue(new DrawingsState(resource.getData(), null, false));
            } else if (resource.isError()) {
                drawingsState.setValue(new DrawingsState(drawingsState.getValue().getDrawings(), resource.getMessage(), false));
            }

            drawingsState.removeSource(result);
        });
    }

    /**
     * Create a new drawing
     */
    public void createNewDrawing(String word) {
        String userId = userRepository.getCurrentUser().getValue() != null ?
                userRepository.getCurrentUser().getValue().getUserId() : null;

        if (userId == null) {
            return;
        }

        Drawing drawing = drawingRepository.createNewDrawing(userId, word);
        currentDrawing.setValue(drawing);
    }

    /**
     * Add a path to the current drawing
     */
    public void addPathToDrawing(Drawing.DrawingPath path) {
        Drawing drawing = currentDrawing.getValue();
        if (drawing != null) {
            drawing.addPath(path);
            currentDrawing.setValue(drawing);
        }
    }

    /**
     * Send a correct guess notification to the repository
     * @param gameId The ID of the game
     */
    public void sendCorrectGuess(String gameId) {
        Log.d(TAG, "✅ Sending correct guess notification for game: " + gameId);
        gameRepository.sendCorrectGuess(gameId);
    }
    
    /**
     * Get top rated drawings
     */
    public LiveData<List<Drawing>> getTopRatedDrawings(int limit) {
        return drawingRepository.getTopRatedDrawings(limit);
    }

    /**
     * Get user's drawing archive state
     */
    public LiveData<DrawingsState> getDrawingsState() {
        return drawingsState;
    }

    /**
     * Get the current drawing being created/edited
     */
    public LiveData<Drawing> getCurrentDrawing() {
        return currentDrawing;
    }

    /**
     * Set the WebSocket callback for game updates
     */
    public void setGameUpdateCallback(WebSocketService.GameUpdateCallback callback) {
        // WebSocket callback for game updates
        gameRepository.setGameUpdateCallback(callback);
    }

    /**
     * Join a game with the given ID
     */
    public void joinGame(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) {
            Log.e(TAG, "Cannot join game with null or empty ID");
            _error.postValue("Invalid game ID");
            return;
        }
        
        Log.i(TAG, "🎲 Joining game with ID: " + gameId);
        isLoading.setValue(true);
        gameRepository.joinGame(gameId).observeForever(resource -> {
            isLoading.setValue(false);
            if (resource.isSuccess() && resource.getData() != null) {
                Game game = resource.getData();
                Log.d(TAG, "Successfully joined game " + gameId + ", state: " + game.getState());
                currentGame.setValue(game);
            } else if (resource.isError()) {
                Log.e(TAG, "Failed to join game: " + resource.getMessage());
                gameRepository.handleError(resource.getMessage());
                _error.postValue("Failed to join game: " + resource.getMessage());
            }
        });
    }
    
    // Removed the entire startGame(String lobbyId) method to delegate game-start handling exclusively to GameViewModel.

    /**
     * Send a chat message in the current game
     */
    public void sendChatMessage(String gameId, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        User currentUser = userRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            return;
        }

        gameRepository.sendChatMessage(gameId, message);

        // Add message to local list (optimistic update)
        List<String> messages = chatMessages.getValue() != null ?
                new ArrayList<>(chatMessages.getValue()) : new ArrayList<>();
        messages.add(currentUser.getUsername() + ": " + message);
        chatMessages.setValue(messages);
    }

    /**
     * Update the drawing path in the current game
     */
    public void updateDrawingPath(String gameId, String pathsJson) {
        if (pathsJson == null || pathsJson.isEmpty()) {
            return;
        }

        gameRepository.updateDrawingPath(gameId, pathsJson);
    }

    // Removed the entire processGameUpdate(String gameData) method to prevent redundant parsing/updating of game state that conflicts with GameViewModel.

    /**
     * Get the current user
     */
    public LiveData<User> getCurrentUser() {
        return userRepository.getCurrentUser();
    }

    /**
     * Update the current game with new data
     * @param game The updated game object
     */
    public void updateCurrentGame(Game game) {
        if (game == null) {
            return;
        }
        
        Log.d(TAG, "Updating current game: " + game.getGameId() + ", Round: " + game.getCurrentRound() + "/" + game.getNumRounds());
        currentGame.setValue(game);
        
        // Update drawing permissions if needed
        User currentUser = userRepository.getCurrentUser().getValue();
        if (currentUser != null && game.getCurrentDrawer() != null) {
            boolean isDrawer = currentUser.getUserId().equals(game.getCurrentDrawer().getUserId());
            Log.d(TAG, "Drawing permissions: " + (isDrawer ? "You can draw" : "You cannot draw"));
        }
    }
    
    /**
     * Get the current game
     */
    public LiveData<Game> getCurrentGame() {
        return currentGame;
    }

    /**
     * Get chat messages
     */
    public LiveData<List<String>> getChatMessages() {
        return chatMessages;
    }
    
    /**
     * Get chat message objects
     */
    public LiveData<List<ChatMessage>> getChatMessageObjects() {
        return chatMessageObjects;
    }
    
    /**
     * Add a chat message to the list
     * @param chatMessage The chat message to add
     */
    public void addChatMessage(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return;
        }
        
        List<ChatMessage> currentMessages = chatMessageObjects.getValue() != null ?
                new ArrayList<>(chatMessageObjects.getValue()) : new ArrayList<>();
        
        // Check for duplicates
        boolean isDuplicate = false;
        for (ChatMessage existingMsg : currentMessages) {
            if (existingMsg.getMessageId() != null && 
                chatMessage.getMessageId() != null &&
                existingMsg.getMessageId().equals(chatMessage.getMessageId())) {
                isDuplicate = true;
                break;
            }
        }
        
        if (!isDuplicate) {
            currentMessages.add(chatMessage);
            chatMessageObjects.setValue(currentMessages);
        }
    }

    /**
     * Get drawing paths
     */
    public LiveData<String> getDrawingPaths() {
        return drawingPaths;
    }

    /**
     * Get game over event
     */
    public LiveData<Boolean> getGameOverEvent() {
        return gameOverEvent;
    }

    /**
     * Get error message
     */
    public LiveData<String> getErrorMessage() {
        return gameRepository.getErrorMessage();
    }

    /**
     * Get drawing details
     */
    public LiveData<Drawing> getDrawingDetails() {
        return drawingDetails;
    }

    /**
     * Get loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Fetch drawings from repository
     */
    public void fetchDrawings() {
        isLoading.setValue(true);
        drawingRepository.getUserDrawings().observeForever(resource -> {
            isLoading.setValue(false);
            if (resource.isSuccess() && resource.getData() != null) {
                drawingsState.setValue(new DrawingsState(resource.getData(), null, false));
            } else if (resource.isError()) {
                drawingsState.setValue(new DrawingsState(null, resource.getMessage(), false));
            }
        });
    }

    /**
     * Fetch details for a specific drawing
     */
    public void fetchDrawingDetails(String drawingId) {
        isLoading.setValue(true);
        drawingRepository.getDrawingById(drawingId).observeForever(resource -> {
            isLoading.setValue(false);
            if (resource.isSuccess() && resource.getData() != null) {
                drawingDetails.setValue(resource.getData());
            } else if (resource.isError()) {
                gameRepository.handleError(resource.getMessage());
            }
        });
    }

    /**
     * Rate a drawing
     */
    public void rateDrawing(String drawingId, int rating) {
        isLoading.setValue(true);
        drawingRepository.rateDrawing(drawingId, rating).observeForever(resource -> {
            isLoading.setValue(false);
            if (resource.isSuccess() && resource.getData() != null) {
                drawingDetails.setValue(resource.getData());
            } else if (resource.isError()) {
                gameRepository.handleError(resource.getMessage());
            }
        });
    }

    /**
     * Save drawing to gallery
     */
    public void saveDrawingToGallery(Bitmap bitmap, String title) {
        drawingRepository.saveDrawingToGallery(bitmap, title);
    }

    /**
     * Get all drawings
     */
    public LiveData<List<Drawing>> getDrawings() {
        return drawingRepository.getAllDrawings();
    }

    /**
     * State class for user's drawing archive
     */
    public static class DrawingsState {
        private final List<Drawing> drawings;
        private final String errorMessage;
        private final boolean isLoading;

        public DrawingsState(List<Drawing> drawings, String errorMessage, boolean isLoading) {
            this.drawings = drawings;
            this.errorMessage = errorMessage;
            this.isLoading = isLoading;
        }

        public List<Drawing> getDrawings() {
            return drawings;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isLoading() {
            return isLoading;
        }

        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }
}
