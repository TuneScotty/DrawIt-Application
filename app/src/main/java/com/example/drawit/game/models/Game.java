package com.example.drawit.game.models;

import com.example.drawit.models.Player; // Import the unified Player model

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core Game model representing the entire game state.
 * This is the central state object that will be synchronized across all clients.
 */
public class Game {
    // Game identification
    private String id;
    
    // Game configuration
    private int totalRounds;
    private int roundDurationSeconds;
    private int wordSelectionTimeSeconds;
    private int reconnectionTimeoutSeconds;
    private int maxGuessesPerSecond;
    
    // Game state
    private GameStatus status;
    private int currentRound;
    private int currentDrawerIndex; // Not used in new gameplay, but kept for compatibility
    private long roundStartTime;
    private long roundEndTime;
    private long wordSelectionEndTime;
    private boolean lobbyVisible;
    private boolean ratingPhase; // Whether the game is in the rating phase
    private long ratingPhaseEndTime; // When the rating phase ends
    
    // Word management
    private String currentWord;
    private List<String> wordChoices;
    private List<String> usedWords;
    
    // Players and guesses
    private List<Player> players;
    private List<Guess> guesses;
    private Map<String, Long> lastGuessTimestamps; // For rate limiting
    
    // Drawing state
    private List<DrawingAction> drawingActions;
    
    // Player drawings - maps player ID to their drawing actions
    private Map<String, List<DrawingAction>> playerDrawings;
    
    // Ratings - maps drawing player ID to a map of rater player ID to rating (1-3)
    private Map<String, Map<String, Integer>> ratings;
    
    /**
     * Default constructor for Firebase
     */
    public Game() {
        this.id = UUID.randomUUID().toString();
        this.players = new ArrayList<>();
        this.guesses = new ArrayList<>();
        this.wordChoices = new ArrayList<>();
        this.usedWords = new ArrayList<>();
        this.drawingActions = new ArrayList<>();
        this.lastGuessTimestamps = new HashMap<>();
        this.playerDrawings = new HashMap<>();
        this.ratings = new HashMap<>();
        this.status = GameStatus.WAITING;
        this.lobbyVisible = true;
        this.ratingPhase = false;
        
        // Default configuration
        this.totalRounds = 3;
        this.roundDurationSeconds = 80;
        this.wordSelectionTimeSeconds = 15;
        this.reconnectionTimeoutSeconds = 30;
        this.maxGuessesPerSecond = 2;
    }
    
    /**
     * Constructor with custom configuration
     */
    public Game(int totalRounds, int roundDurationSeconds, int wordSelectionTimeSeconds) {
        this();
        this.totalRounds = totalRounds;
        this.roundDurationSeconds = roundDurationSeconds;
        this.wordSelectionTimeSeconds = wordSelectionTimeSeconds;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public GameStatus getStatus() {
        return status;
    }
    
    public void setStatus(GameStatus status) {
        this.status = status;
        // When game starts, make lobby invisible
        if (status == GameStatus.STARTED) {
            this.lobbyVisible = false;
        }
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }
    
    public int getTotalRounds() {
        return totalRounds;
    }
    
    public void setTotalRounds(int totalRounds) {
        this.totalRounds = totalRounds;
    }
    
    public int getCurrentDrawerIndex() {
        return currentDrawerIndex;
    }
    
    public void setCurrentDrawerIndex(int currentDrawerIndex) {
        this.currentDrawerIndex = currentDrawerIndex;
    }
    
    public String getCurrentWord() {
        return currentWord;
    }
    
    public void setCurrentWord(String currentWord) {
        this.currentWord = currentWord;
        if (currentWord != null && !currentWord.isEmpty()) {
            this.usedWords.add(currentWord);
        }
    }
    
    public List<String> getWordChoices() {
        return wordChoices;
    }
    
    public void setWordChoices(List<String> wordChoices) {
        this.wordChoices = wordChoices;
    }
    
    public List<Player> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<Player> players) {
        this.players = players;
    }
    
    public List<Guess> getGuesses() {
        return guesses;
    }
    
    public void setGuesses(List<Guess> guesses) {
        this.guesses = guesses;
    }
    
    public long getRoundStartTime() {
        return roundStartTime;
    }
    
    public void setRoundStartTime(long roundStartTime) {
        this.roundStartTime = roundStartTime;
    }
    
    public long getRoundEndTime() {
        return roundEndTime;
    }
    
    public void setRoundEndTime(long roundEndTime) {
        this.roundEndTime = roundEndTime;
    }
    
    public boolean isLobbyVisible() {
        return lobbyVisible;
    }
    
    public void setLobbyVisible(boolean lobbyVisible) {
        this.lobbyVisible = lobbyVisible;
    }
    
    public List<DrawingAction> getDrawingActions() {
        return drawingActions;
    }
    
    public void setDrawingActions(List<DrawingAction> drawingActions) {
        this.drawingActions = drawingActions;
    }
    
    public Map<String, List<DrawingAction>> getPlayerDrawings() {
        return playerDrawings;
    }
    
    public void setPlayerDrawings(Map<String, List<DrawingAction>> playerDrawings) {
        this.playerDrawings = playerDrawings;
    }
    
    public List<DrawingAction> getPlayerDrawing(String playerId) {
        return playerDrawings.get(playerId);
    }
    
    public void addPlayerDrawingAction(String playerId, DrawingAction action) {
        if (!playerDrawings.containsKey(playerId)) {
            playerDrawings.put(playerId, new ArrayList<>());
        }
        playerDrawings.get(playerId).add(action);
    }
    
    public void clearPlayerDrawing(String playerId) {
        if (playerDrawings.containsKey(playerId)) {
            playerDrawings.get(playerId).clear();
        }
    }
    
    public Map<String, Map<String, Integer>> getRatings() {
        return ratings;
    }
    
    public void setRatings(Map<String, Map<String, Integer>> ratings) {
        this.ratings = ratings;
    }
    
    public void addRating(String drawingPlayerId, String raterPlayerId, int rating) {
        if (!ratings.containsKey(drawingPlayerId)) {
            ratings.put(drawingPlayerId, new HashMap<>());
        }
        ratings.get(drawingPlayerId).put(raterPlayerId, rating);
    }
    
    public int getRating(String drawingPlayerId, String raterPlayerId) {
        if (ratings.containsKey(drawingPlayerId) && 
            ratings.get(drawingPlayerId).containsKey(raterPlayerId)) {
            return ratings.get(drawingPlayerId).get(raterPlayerId);
        }
        return 0; // No rating yet
    }
    
    public int getTotalRating(String playerId) {
        if (!ratings.containsKey(playerId)) {
            return 0;
        }
        
        int total = 0;
        for (Integer rating : ratings.get(playerId).values()) {
            total += rating;
        }
        return total;
    }
    
    public boolean isRatingPhase() {
        return ratingPhase;
    }
    
    public void setRatingPhase(boolean ratingPhase) {
        this.ratingPhase = ratingPhase;
    }
    
    public long getRatingPhaseEndTime() {
        return ratingPhaseEndTime;
    }
    
    public void setRatingPhaseEndTime(long ratingPhaseEndTime) {
        this.ratingPhaseEndTime = ratingPhaseEndTime;
    }
    
    public boolean isRatingPhaseTimedOut(long currentTime) {
        return ratingPhaseEndTime > 0 && currentTime > ratingPhaseEndTime;
    }
    
    public long getWordSelectionEndTime() {
        return wordSelectionEndTime;
    }
    
    public void setWordSelectionEndTime(long wordSelectionEndTime) {
        this.wordSelectionEndTime = wordSelectionEndTime;
    }
    
    public List<String> getUsedWords() {
        return usedWords;
    }
    
    public void setUsedWords(List<String> usedWords) {
        this.usedWords = usedWords;
    }
    
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = roundDurationSeconds;
    }
    
    public int getWordSelectionTimeSeconds() {
        return wordSelectionTimeSeconds;
    }
    
    public void setWordSelectionTimeSeconds(int wordSelectionTimeSeconds) {
        this.wordSelectionTimeSeconds = wordSelectionTimeSeconds;
    }
    
    public int getReconnectionTimeoutSeconds() {
        return reconnectionTimeoutSeconds;
    }
    
    public void setReconnectionTimeoutSeconds(int reconnectionTimeoutSeconds) {
        this.reconnectionTimeoutSeconds = reconnectionTimeoutSeconds;
    }
    
    // Game state helper methods
    
    /**
     * Get the current drawer player
     */
    public Player getCurrentDrawer() {
        if (players.isEmpty() || currentDrawerIndex < 0 || currentDrawerIndex >= players.size()) {
            return null;
        }
        return players.get(currentDrawerIndex);
    }
    
    /**
     * Check if all players have guessed correctly
     */
    public boolean allPlayersGuessedCorrectly() {
        if (players.isEmpty()) return false;
        
        int correctGuesses = 0;
        for (Player player : players) {
            // Skip the drawer and disconnected players
            if (player.isDrawing() || !player.isConnected()) continue;
            
            if (player.isHasGuessedCorrectly()) {
                correctGuesses++;
            }
        }
        
        // Count connected non-drawing players
        int activeGuessers = 0;
        for (Player player : players) {
            if (!player.isDrawing() && player.isConnected()) {
                activeGuessers++;
            }
        }
        
        return activeGuessers > 0 && correctGuesses == activeGuessers;
    }
    
    /**
     * Get the number of active players (connected)
     */
    public int getActivePlayerCount() {
        int count = 0;
        for (Player player : players) {
            if (player.isConnected()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Check if a guess is rate-limited
     */
    public boolean isGuessRateLimited(String playerId, long currentTime) {
        Long lastGuessTime = lastGuessTimestamps.get(playerId);
        if (lastGuessTime == null) {
            lastGuessTimestamps.put(playerId, currentTime);
            return false;
        }
        
        // Check if enough time has passed since the last guess
        long timeSinceLastGuess = currentTime - lastGuessTime;
        if (timeSinceLastGuess < 1000 / maxGuessesPerSecond) {
            return true; // Rate limited
        }
        
        // Update the last guess timestamp
        lastGuessTimestamps.put(playerId, currentTime);
        return false;
    }
    
    /**
     * Add a drawing action to the game state
     */
    public void addDrawingAction(DrawingAction action) {
        this.drawingActions.add(action);
    }
    
    /**
     * Clear all drawing actions
     */
    public void clearDrawingActions() {
        this.drawingActions.clear();
    }
    
    /**
     * Add a player to the game
     */
    public void addPlayer(Player player) {
        this.players.add(player);
    }
    
    /**
     * Remove a player from the game
     */
    public boolean removePlayer(String playerId) {
        return this.players.removeIf(player -> player.getId().equals(playerId));
    }
    
    /**
     * Find a player by ID
     */
    public Player findPlayerById(String playerId) {
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                return player;
            }
        }
        return null;
    }
    
    /**
     * Add a guess to the game
     */
    public void addGuess(Guess guess) {
        this.guesses.add(guess);
    }
    
    /**
     * Check if a player has already guessed correctly
     */
    public boolean hasPlayerGuessedCorrectly(String playerId) {
        Player player = findPlayerById(playerId);
        return player != null && player.isHasGuessedCorrectly();
    }
    
    /**
     * Reset player guessed status for a new round
     */
    public void resetPlayerGuessedStatus() {
        for (Player player : players) {
            player.setHasGuessedCorrectly(false);
        }
    }
    
    /**
     * Reset drawing status for all players
     */
    public void resetDrawingStatus() {
        for (Player player : players) {
            player.setDrawing(false);
        }
    }
    
    /**
     * Set the current drawer based on the drawer index
     */
    public void updateCurrentDrawer() {
        resetDrawingStatus();
        Player drawer = getCurrentDrawer();
        if (drawer != null) {
            drawer.setDrawing(true);
        }
    }
    
    /**
     * Check if the round has timed out
     */
    public boolean isRoundTimedOut(long currentTime) {
        return currentTime >= roundEndTime;
    }
    
    /**
     * Check if word selection has timed out
     */
    public boolean isWordSelectionTimedOut(long currentTime) {
        return currentTime >= wordSelectionEndTime;
    }
    
    /**
     * Get the time remaining in the current round in seconds
     */
    public int getRoundTimeRemainingSeconds(long currentTime) {
        long timeRemaining = roundEndTime - currentTime;
        return (int) Math.max(0, timeRemaining / 1000);
    }
    
    /**
     * Get the time remaining for word selection in seconds
     */
    public int getWordSelectionTimeRemainingSeconds(long currentTime) {
        long timeRemaining = wordSelectionEndTime - currentTime;
        return (int) Math.max(0, timeRemaining / 1000);
    }
    
    /**
     * Check if a player has been disconnected for too long
     */
    public boolean isPlayerDisconnectedTooLong(Player player, long currentTime) {
        return !player.isConnected() && 
               (currentTime - player.getDisconnectionTime()) > (reconnectionTimeoutSeconds * 1000);
    }
}
