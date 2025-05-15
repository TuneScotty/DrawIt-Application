package com.example.drawit.game;

import android.util.Log;

import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.game.models.Game;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.Guess;
import com.example.drawit.models.Player; // Using the unified Player model

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Core game manager that handles game logic, state transitions, and events.
 * This class is responsible for managing the game lifecycle and enforcing game rules.
 */
public class GameManager {
    private static final String TAG = "GameManager";
    
    // Constants for scoring
    private static final int BASE_POINTS_FOR_CORRECT_GUESS = 100;
    private static final int BONUS_POINTS_FOR_DRAWER_PER_CORRECT_GUESS = 50;
    private static final int MAX_WORD_CHOICES = 3;
    
    // Game state
    private Game game;
    private WordProvider wordProvider;
    private Random random;
    
    // Listeners
    private GameEventListener eventListener;
    
    /**
     * Constructor with default configuration
     */
    public GameManager(WordProvider wordProvider) {
        this.game = new Game();
        this.wordProvider = wordProvider;
        this.random = new Random();
    }
    
    /**
     * Constructor with custom configuration
     */
    public GameManager(WordProvider wordProvider, int totalRounds, int roundDurationSeconds, int wordSelectionTimeSeconds) {
        this.game = new Game(totalRounds, roundDurationSeconds, wordSelectionTimeSeconds);
        this.wordProvider = wordProvider;
        this.random = new Random();
    }
    
    /**
     * Set the game event listener
     */
    public void setEventListener(GameEventListener eventListener) {
        this.eventListener = eventListener;
    }
    
    /**
     * Get the current game state
     */
    public Game getGame() {
        return game;
    }
    
    /**
     * Set the game state (for loading from persistence)
     */
    public void setGame(Game game) {
        this.game = game;
    }
    
    /**
     * Create a new player and add them to the game
     */
    public Player addPlayer(String playerId, String playerName) {
        // Check if the game has already started
        if (game.getStatus() != GameStatus.WAITING) {
            Log.e(TAG, "Cannot add player to a game that has already started");
            return null;
        }
        
        // Check if player already exists
        Player existingPlayer = game.findPlayerById(playerId);
        if (existingPlayer != null) {
            Log.d(TAG, "Player already exists: " + playerId);
            return existingPlayer;
        }
        
        // Create and add the new player
        Player player = new Player(playerId, playerName);
        
        // If this is the first player, make them the host
        if (game.getPlayers().isEmpty()) {
            player.setHost(true);
        }
        
        game.addPlayer(player);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onPlayerJoined(game, player);
        }
        
        return player;
    }
    
    /**
     * Remove a player from the game
     */
    public boolean removePlayer(String playerId) {
        // Find the player
        Player player = game.findPlayerById(playerId);
        if (player == null) {
            Log.e(TAG, "Player not found: " + playerId);
            return false;
        }
        
        // Check if this is the current drawer
        boolean isDrawer = game.getCurrentDrawer() != null && 
                          playerId.equals(game.getCurrentDrawer().getId());
        
        // Remove the player
        game.removePlayer(playerId);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onPlayerLeft(game, player);
        }
        
        // If this was the drawer, handle that special case
        if (isDrawer) {
            handleDrawerLeft();
        }
        
        // If this was the host, assign a new host
        if (player.isHost() && !game.getPlayers().isEmpty()) {
            Player newHost = game.getPlayers().get(0);
            newHost.setHost(true);
            
            // Notify listeners
            if (eventListener != null) {
                eventListener.onHostChanged(game, newHost);
            }
        }
        
        return true;
    }
    
    /**
     * Mark a player as disconnected
     */
    public boolean markPlayerDisconnected(String playerId) {
        // Find the player
        Player player = game.findPlayerById(playerId);
        if (player == null) {
            Log.e(TAG, "Player not found: " + playerId);
            return false;
        }
        
        // Mark as disconnected
        player.setConnected(false);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onPlayerDisconnected(game, player);
        }
        
        // If this is the drawer, handle that special case
        if (game.getCurrentDrawer() != null && 
            playerId.equals(game.getCurrentDrawer().getId())) {
            handleDrawerLeft();
        }
        
        return true;
    }
    
    /**
     * Reconnect a player
     */
    public boolean reconnectPlayer(String playerId) {
        // Find the player
        Player player = game.findPlayerById(playerId);
        if (player == null) {
            Log.e(TAG, "Player not found: " + playerId);
            return false;
        }
        
        // Mark as connected
        player.setConnected(true);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onPlayerReconnected(game, player);
        }
        
        return true;
    }
    
    /**
     * Start the game
     */
    public boolean startGame() {
        // Check if there are enough players
        if (game.getPlayers().size() < 2) {
            Log.e(TAG, "Not enough players to start the game");
            return false;
        }
        
        // Check if the game is in the waiting state
        if (game.getStatus() != GameStatus.WAITING) {
            Log.e(TAG, "Game is not in the waiting state");
            return false;
        }
        
        // Initialize the game
        game.setStatus(GameStatus.STARTED);
        game.setCurrentRound(1);
        game.setCurrentDrawerIndex(0);
        
        // Start the first round
        startRound();
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onGameStarted(game);
        }
        
        return true;
    }
    
    /**
     * Start a new round
     */
    private void startRound() {
        // Reset round state
        game.resetPlayerGuessedStatus();
        game.resetDrawingStatus();
        game.clearDrawingActions();
        
        // Update the current drawer
        game.updateCurrentDrawer();
        
        // Generate word choices for the drawer
        Player drawer = game.getCurrentDrawer();
        if (drawer == null) {
            Log.e(TAG, "No drawer found for the current round");
            endGame("No drawer found for the current round");
            return;
        }
        
        // Generate word choices
        List<String> wordChoices = generateWordChoices();
        game.setWordChoices(wordChoices);
        
        // Set the word selection timeout
        long currentTime = System.currentTimeMillis();
        game.setWordSelectionEndTime(currentTime + (game.getWordSelectionTimeSeconds() * 1000));
        
        // Update game status
        game.setStatus(GameStatus.WORD_SELECTION);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onRoundStarted(game);
            eventListener.onWordSelectionStarted(game, drawer, wordChoices);
        }
    }
    
    /**
     * Generate word choices for the drawer
     */
    private List<String> generateWordChoices() {
        List<String> allWords = wordProvider.getWords();
        List<String> usedWords = game.getUsedWords();
        List<String> availableWords = new ArrayList<>(allWords);
        
        // Remove already used words
        availableWords.removeAll(usedWords);
        
        // If we've used too many words, reset the available words
        if (availableWords.size() < MAX_WORD_CHOICES) {
            availableWords = new ArrayList<>(allWords);
        }
        
        // Shuffle the available words
        Collections.shuffle(availableWords, random);
        
        // Take the first MAX_WORD_CHOICES words
        return availableWords.subList(0, Math.min(MAX_WORD_CHOICES, availableWords.size()));
    }
    
    /**
     * Handle word selection by the drawer
     */
    public void selectWord(String playerId, String word) {
        // Check if the game is in the word selection state
        if (game.getStatus() != GameStatus.WORD_SELECTION) {
            Log.e(TAG, "Game is not in the word selection state");
            return;
        }
        
        // Check if the player is the current drawer
        Player drawer = game.getCurrentDrawer();
        if (drawer == null || !playerId.equals(drawer.getId())) {
            Log.e(TAG, "Player is not the current drawer");
            return;
        }
        
        // Set the selected word
        game.setCurrentWord(word);
        // Note: setCurrentWord already adds the word to usedWords in the Game class
        
        // Start the drawing phase
        startDrawingPhase();
    }
    
    /**
     * Handle word selection timeout
     */
    public void handleWordSelectionTimeout() {
        // Check if the game is still in the word selection state
        if (game.getStatus() != GameStatus.WORD_SELECTION) {
            return;
        }
        
        // Check if the timeout has actually occurred
        if (!game.isWordSelectionTimedOut(System.currentTimeMillis())) {
            return;
        }
        
        // Auto-select a word
        List<String> wordChoices = game.getWordChoices();
        if (wordChoices != null && !wordChoices.isEmpty()) {
            String word = wordChoices.get(0);
            game.setCurrentWord(word);
            // Note: setCurrentWord already adds the word to usedWords in the Game class
            
            // Start the drawing phase
            startDrawingPhase();
        } else {
            // No words available, end the round
            endRound();
        }
    }
    
    /**
     * Start the drawing phase
     */
    private void startDrawingPhase() {
        // Set the round start and end times
        long currentTime = System.currentTimeMillis();
        game.setRoundStartTime(currentTime);
        game.setRoundEndTime(currentTime + (game.getRoundDurationSeconds() * 1000));
        
        // Update game status
        game.setStatus(GameStatus.DRAWING);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onDrawingPhaseStarted(game);
        }
    }
    
    /**
     * Handle a drawing action
     */
    public void handleDrawingAction(String playerId, DrawingAction action) {
        // Check if the game is in the drawing state
        if (game.getStatus() != GameStatus.DRAWING) {
            Log.e(TAG, "Game is not in the drawing state");
            return;
        }
        
        // Add the drawing action
        game.addDrawingAction(action);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onDrawingActionReceived(game, action);
        }
    }
    
    /**
     * Handle a guess from a player
     */
    public void handleGuess(String playerId, String guessText) {
        // Check if the game is in the drawing state
        if (game.getStatus() != GameStatus.DRAWING) {
            Log.e(TAG, "Game is not in the drawing state");
            return;
        }
        
        // Find the player
        Player player = game.findPlayerById(playerId);
        if (player == null) {
            Log.e(TAG, "Player not found: " + playerId);
            return;
        }
        
        // Check if this is the drawer (drawer can't guess)
        Player drawer = game.getCurrentDrawer();
        if (drawer != null && playerId.equals(drawer.getId())) {
            Log.e(TAG, "Drawer cannot guess");
            return;
        }
        
        // Check if the player has already guessed correctly
        if (player.isHasGuessedCorrectly()) {
            Log.d(TAG, "Player has already guessed correctly");
            return;
        }
        
        // Check rate limiting
        if (isPlayerRateLimited(playerId)) {
            Log.d(TAG, "Player is rate limited");
            return;
        }
        
        // Note: Rate limiting and timestamp updates are handled by game.isGuessRateLimited
        
        // Create the guess
        Guess guess = new Guess(playerId, guessText, System.currentTimeMillis());
        game.addGuess(guess);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onGuessReceived(game, guess);
        }
        
        // Check if the guess is correct
        if (isGuessCorrect(guessText, game.getCurrentWord())) {
            handleCorrectGuess(player);
        }
    }
    
    /**
     * Check if a guess is correct
     */
    private boolean isGuessCorrect(String guess, String word) {
        return guess.trim().equalsIgnoreCase(word.trim());
    }
    
    /**
     * Check if a player is rate limited for guessing
     */
    private boolean isPlayerRateLimited(String playerId) {
        // Use the Game's built-in rate limiting method
        return game.isGuessRateLimited(playerId, System.currentTimeMillis());
    }
    
    // Note: Timestamp updates are handled by game.isGuessRateLimited
    
    /**
     * Handle a correct guess
     */
    private void handleCorrectGuess(Player player) {
        // Mark the player as having guessed correctly
        player.setHasGuessedCorrectly(true);
        
        // Calculate points based on time remaining
        int timeRemainingSeconds = (int) ((game.getRoundEndTime() - System.currentTimeMillis()) / 1000);
        int points = calculatePointsForCorrectGuess(timeRemainingSeconds);
        
        // Award points to the player
        player.addPoints(points);
        
        // Award points to the drawer
        Player drawer = game.getCurrentDrawer();
        if (drawer != null) {
            drawer.addPoints(BONUS_POINTS_FOR_DRAWER_PER_CORRECT_GUESS);
        }
        
        // Check if all players have guessed correctly
        if (game.allPlayersGuessedCorrectly()) {
            // End the round early
            endRound();
        }
    }
    
    /**
     * Calculate points for a correct guess based on time remaining
     */
    private int calculatePointsForCorrectGuess(int timeRemainingSeconds) {
        // Base points plus bonus for time remaining
        return BASE_POINTS_FOR_CORRECT_GUESS + timeRemainingSeconds;
    }
    
    /**
     * Handle round timeout
     */
    public void handleRoundTimeout() {
        // In the new gameplay, transition to rating phase instead of ending the round
        startRatingPhase();
    }
    
    /**
     * Start the rating phase
     */
    public void startRatingPhase() {
        // Update game status
        game.setStatus(GameStatus.RATING);
        game.setRatingPhase(true);
        
        // Set the rating phase timeout (30 seconds per player to rate)
        long currentTime = System.currentTimeMillis();
        int ratingTimeSeconds = 30 * (game.getPlayers().size() - 1); // Time to rate each player's drawing
        game.setRatingPhaseEndTime(currentTime + (ratingTimeSeconds * 1000));
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onRatingPhaseStarted(game);
        }
    }
    
    /**
     * Handle rating phase timeout
     */
    public void handleRatingPhaseTimeout() {
        // End the round after ratings are done
        endRound();
    }
    
    /**
     * End the current round
     */
    private void endRound() {
        // Update game status
        game.setStatus(GameStatus.ROUND_ENDED);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onRoundEnded(game);
        }
        
        // Check if this was the last round
        if (game.getCurrentRound() >= game.getTotalRounds()) {
            // End the game
            endGame("Game completed");
        } else {
            // Schedule the next round
            game.setCurrentRound(game.getCurrentRound() + 1);
            
            // Move to the next drawer
            int nextDrawerIndex = (game.getCurrentDrawerIndex() + 1) % game.getPlayers().size();
            game.setCurrentDrawerIndex(nextDrawerIndex);
            
            // Start the next round
            startRound();
        }
    }
    
    /**
     * Handle the case where the drawer leaves or disconnects
     */
    private void handleDrawerLeft() {
        // Skip to the next drawer
        int nextDrawerIndex = (game.getCurrentDrawerIndex() + 1) % game.getPlayers().size();
        game.setCurrentDrawerIndex(nextDrawerIndex);
        
        // End the current round
        endRound();
    }
    
    /**
     * End the game
     */
    public void endGame(String reason) {
        // Update game status
        game.setStatus(GameStatus.ENDED);
        
        // Notify listeners
        if (eventListener != null) {
            eventListener.onGameEnded(game, reason);
        }
    }
    
    /**
     * Interface for game event listeners
     */
    public interface GameEventListener {
        void onGameStarted(Game game);
        void onRoundStarted(Game game);
        void onWordSelectionStarted(Game game, Player drawer, List<String> wordChoices);
        void onDrawingPhaseStarted(Game game);
        void onRatingPhaseStarted(Game game);
        void onDrawingActionReceived(Game game, DrawingAction action);
        void onGuessReceived(Game game, Guess guess);
        void onRoundEnded(Game game);
        void onGameEnded(Game game, String reason);
        void onPlayerJoined(Game game, Player player);
        void onPlayerLeft(Game game, Player player);
        void onPlayerDisconnected(Game game, Player player);
        void onPlayerReconnected(Game game, Player player);
        void onHostChanged(Game game, Player newHost);
    }
}
