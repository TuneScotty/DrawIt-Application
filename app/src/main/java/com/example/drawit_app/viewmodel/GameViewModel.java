package com.example.drawit_app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.User;
import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.GameRepository;
import com.example.drawit_app.repository.LobbyRepository;
import com.example.drawit_app.repository.UserRepository;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for game-related operations
 */
@HiltViewModel
public class GameViewModel extends ViewModel {
    
    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    
    // State of the current game
    private final MediatorLiveData<GameState> gameState = new MediatorLiveData<>();
    
    // Time remaining in current round
    private final MediatorLiveData<Integer> timeRemaining = new MediatorLiveData<>();
    
    @Inject
    public GameViewModel(GameRepository gameRepository, LobbyRepository lobbyRepository, UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        
        // Initialize states
        gameState.setValue(new GameState(null, null, false));
        
        // Observe current game
        LiveData<Game> currentGame = gameRepository.getCurrentGame();
        gameState.addSource(currentGame, game -> {
            GameState currentState = gameState.getValue();
            if (currentState != null) {
                gameState.setValue(new GameState(game, currentState.getErrorMessage(), false));
            } else {
                gameState.setValue(new GameState(game, null, false));
            }
        });
        
        // Observe time remaining
        LiveData<Integer> timeRemainingLiveData = gameRepository.getTimeRemaining();
        timeRemaining.addSource(timeRemainingLiveData, time -> {
            timeRemaining.setValue(time);
        });
    }
    
    /**
     * Start a new game from the current lobby (host only)
     */
    public void startGame() {
        // Check if in a lobby
        if (lobbyRepository.getCurrentLobby().getValue() == null) {
            gameState.setValue(new GameState(null, "Not in a lobby", false));
            return;
        }
        
        // Set loading state
        gameState.setValue(new GameState(null, null, true));
        
        // Call repository to start game
        String lobbyId = lobbyRepository.getCurrentLobby().getValue().getLobbyId();
        LiveData<Resource<Game>> result = gameRepository.startGame(lobbyId);
        gameState.addSource(result, resource -> {
            if (resource.isLoading()) {
                // Already set loading state above
            } else if (resource.isSuccess()) {
                // Success handled by observing gameRepository.getCurrentGame()
                // Just clear error message
                gameState.setValue(new GameState(gameState.getValue().getGame(), null, false));
            } else if (resource.isError()) {
                gameState.setValue(new GameState(null, resource.getMessage(), false));
            }
            
            gameState.removeSource(result);
        });
    }
    
    /**
     * Submit a drawing for the current game and round
     */
    public void submitDrawing(Drawing drawing) {
        // Check if in a game and game is in drawing phase
        GameState currentState = gameState.getValue();
        if (currentState == null || currentState.getGame() == null) {
            return;
        }
        
        Game game = currentState.getGame();
        if (game.getGameState() != Game.GameState.DRAWING) {
            return;
        }
        
        // Set user ID
        String userId = userRepository.getCurrentUser().getValue() != null ? 
                userRepository.getCurrentUser().getValue().getUserId() : null;
        
        if (userId == null) {
            return;
        }
        
        drawing.setUserId(userId);
        
        // Submit drawing
        gameRepository.submitDrawing(drawing);
    }
    
    /**
     * Rate a drawing in the current game
     */
    public void rateDrawing(String drawingId, float rating) {
        // Check if in a game and game is in voting phase
        GameState currentState = gameState.getValue();
        if (currentState == null || currentState.getGame() == null) {
            return;
        }
        
        Game game = currentState.getGame();
        if (game.getGameState() != Game.GameState.VOTING) {
            return;
        }
        
        // Rate drawing
        gameRepository.rateDrawing(drawingId, rating);
    }
    
    /**
     * Send a chat message in the current game
     * @param gameId The game ID
     * @param message The message to send
     */
    public void sendChatMessage(String gameId, String message) {
        gameRepository.sendChatMessage(gameId, message);
    }
    
    /**
     * Update the drawing path for the current game
     * @param gameId The game ID
     * @param pathsJson JSON representation of the paths
     */
    public void updateDrawingPath(String gameId, String pathsJson) {
        gameRepository.updateDrawingPath(gameId, pathsJson);
    }
    
    /**
     * Get the current user
     * @return LiveData containing the current user
     */
    public LiveData<User> getCurrentUser() {
        return userRepository.getCurrentUser();
    }
    
    /**
     * Get the current game
     * @return LiveData containing the current game
     */
    public LiveData<Game> getCurrentGame() {
        return gameRepository.getCurrentGame();
    }
    
    /**
     * Get chat messages for the current game
     * @return LiveData containing the list of chat messages
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return gameRepository.getChatMessages();
    }
    
    /**
     * Get drawing paths for the current game
     * @return LiveData containing the JSON representation of drawing paths
     */
    public LiveData<String> getDrawingPaths() {
        return gameRepository.getDrawingPaths();
    }
    
    /**
     * Get error messages
     * @return LiveData containing error messages
     */
    public LiveData<String> getErrorMessage() {
        return gameRepository.getErrorMessage();
    }
    
    /**
     * Get the current game
     */
    public LiveData<GameState> getGameState() {
        return gameState;
    }
    
    /**
     * Get time remaining in the current round
     */
    public LiveData<Integer> getTimeRemaining() {
        return timeRemaining;
    }
    
    /**
     * Get player scores from the current game
     */
    public Map<String, Float> getPlayerScores() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            return currentState.getGame().getPlayerScores();
        }
        return null;
    }
    
    /**
     * Get drawings for the current round
     */
    public List<Drawing> getCurrentRoundDrawings() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            return currentState.getGame().getCurrentRoundDrawings();
        }
        return null;
    }
    
    /**
     * Get the current word to draw
     */
    public String getCurrentWord() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            return currentState.getGame().getCurrentWord();
        }
        return null;
    }
    
    /**
     * Get the current round number
     */
    public int getCurrentRound() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            return currentState.getGame().getCurrentRound();
        }
        return 0;
    }
    
    /**
     * Get the total number of rounds
     */
    public int getTotalRounds() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            return currentState.getGame().getTotalRounds();
        }
        return 0;
    }
    
    /**
     * Check if the current user is the host of the game's lobby
     */
    public boolean isCurrentUserHost() {
        GameState currentState = gameState.getValue();
        if (currentState != null && currentState.getGame() != null) {
            String userId = userRepository.getCurrentUser().getValue() != null ? 
                    userRepository.getCurrentUser().getValue().getUserId() : null;
            
            if (userId == null) {
                return false;
            }
            
            // Get the lobby
            String lobbyId = currentState.getGame().getLobbyId();
            if (lobbyRepository.getCurrentLobby().getValue() != null &&
                lobbyRepository.getCurrentLobby().getValue().getLobbyId().equals(lobbyId)) {
                return userId.equals(lobbyRepository.getCurrentLobby().getValue().getHostId());
            }
        }
        return false;
    }
    
    /**
     * State class for the current game
     */
    public static class GameState {
        private final Game game;
        private final String errorMessage;
        private final boolean isLoading;
        
        public GameState(Game game, String errorMessage, boolean isLoading) {
            this.game = game;
            this.errorMessage = errorMessage;
            this.isLoading = isLoading;
        }
        
        public Game getGame() {
            return game;
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
        
        public boolean isInGame() {
            return game != null;
        }
        
        public boolean isDrawingPhase() {
            return game != null && game.getGameState() == Game.GameState.DRAWING;
        }
        
        public boolean isVotingPhase() {
            return game != null && game.getGameState() == Game.GameState.VOTING;
        }
        
        public boolean isLeaderboardPhase() {
            return game != null && game.getGameState() == Game.GameState.LEADERBOARD;
        }
        
        public boolean isFinished() {
            return game != null && game.getGameState() == Game.GameState.FINISHED;
        }
    }
}
