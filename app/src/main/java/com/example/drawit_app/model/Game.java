package com.example.drawit_app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.drawit_app.data.GameStateConverter;
import com.example.drawit_app.data.PlayerScoreConverter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete game session with all rounds and player scores.
 */
@Entity(tableName = "games")
@TypeConverters({GameStateConverter.class, PlayerScoreConverter.class})
public class Game {
    
    public enum GameState {
        WAITING,    // Game created but not started
        DRAWING,    // Players are drawing
        VOTING,     // Players are voting on drawings
        LEADERBOARD, // Round results are shown
        FINISHED    // Game is over
    }
    
    @PrimaryKey
    @NonNull
    private String gameId;
    
    private String lobbyId;
    private Date startTime;
    private Date endTime;
    private int currentRound;
    private int totalRounds;
    private int roundDurationSeconds;
    private GameState gameState;
    private String currentWord;
    private String currentDrawerId;
    
    // Maps userId to their current score
    private Map<String, Float> playerScores;
    
    // List of drawings for the current round
    private transient List<Drawing> currentRoundDrawings;
    
    // Timer-related fields
    private transient int remainingTime; // Seconds remaining in current round
    
    // Transient field for current drawer (not saved in database)
    private transient User currentDrawer;
    
    public Game() {
        this.playerScores = new HashMap<>();
        this.currentRoundDrawings = new ArrayList<>();
    }
    
    @Ignore
    public Game(@NonNull String gameId, String lobbyId, int totalRounds, int roundDurationSeconds) {
        this.gameId = gameId;
        this.lobbyId = lobbyId;
        this.startTime = new Date();
        this.currentRound = 0;
        this.totalRounds = totalRounds;
        this.roundDurationSeconds = roundDurationSeconds;
        this.gameState = GameState.WAITING;
        this.playerScores = new HashMap<>();
        this.currentRoundDrawings = new ArrayList<>();
    }
    
    @NonNull
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(@NonNull String gameId) {
        this.gameId = gameId;
    }
    
    public String getLobbyId() {
        return lobbyId;
    }
    
    public void setLobbyId(String lobbyId) {
        this.lobbyId = lobbyId;
    }
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
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
    
    /**
     * Alias for setTotalRounds to maintain API compatibility
     * @param numRounds The number of rounds for the game
     */
    public void setNumRounds(int numRounds) {
        this.totalRounds = numRounds;
    }
    
    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }
    
    public void setRoundDurationSeconds(int roundDurationSeconds) {
        this.roundDurationSeconds = roundDurationSeconds;
    }
    
    public GameState getGameState() {
        return gameState;
    }
    
    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }
    
    /**
     * Convenience method for getting game state (used by fragments)
     * @return Current game state
     */
    public GameState getState() {
        return gameState;
    }
    
    /**
     * Get remaining time in current round
     * @return Seconds remaining
     */
    public int getRemainingTime() {
        return remainingTime;
    }
    
    /**
     * Set remaining time in current round
     * @param remainingTime Seconds remaining
     */
    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }
    
    public String getCurrentWord() {
        return currentWord;
    }
    
    public void setCurrentWord(String currentWord) {
        this.currentWord = currentWord;
    }
    
    public String getCurrentDrawerId() {
        return currentDrawerId;
    }
    
    public void setCurrentDrawerId(String currentDrawerId) {
        this.currentDrawerId = currentDrawerId;
    }
    
    /**
     * Get the current user who is drawing
     * @return User object of the current drawer
     */
    public User getCurrentDrawer() {
        return currentDrawer;
    }
    
    /**
     * Set the current drawer user
     * @param currentDrawer User who is currently drawing
     */
    public void setCurrentDrawer(User currentDrawer) {
        this.currentDrawer = currentDrawer;
        if (currentDrawer != null) {
            this.currentDrawerId = currentDrawer.getUserId();
        }
    }
    
    public Map<String, Float> getPlayerScores() {
        return playerScores;
    }
    
    public void setPlayerScores(Map<String, Float> playerScores) {
        this.playerScores = playerScores;
    }
    
    public List<Drawing> getCurrentRoundDrawings() {
        return currentRoundDrawings;
    }
    
    public void setCurrentRoundDrawings(List<Drawing> currentRoundDrawings) {
        this.currentRoundDrawings = currentRoundDrawings;
    }
    
    /**
     * Adds a drawing to the current round
     * @param drawing Drawing to add
     */
    public void addDrawing(Drawing drawing) {
        currentRoundDrawings.add(drawing);
    }
    
    /**
     * Updates a player's score for the current round
     * @param userId User ID
     * @param rating Rating received for this round
     */
    public void updatePlayerScore(String userId, float rating) {
        Float currentScore = playerScores.getOrDefault(userId, 0.0f);
        playerScores.put(userId, currentScore + rating);
    }
    
    /**
     * Moves the game to the next round
     * @return true if there are more rounds, false if the game is over
     */
    public boolean nextRound() {
        currentRound++;
        if (currentRound > totalRounds) {
            gameState = GameState.FINISHED;
            endTime = new Date();
            return false;
        }
        currentRoundDrawings.clear();
        return true;
    }
    
    /**
     * Gets a sorted list of player scores for the leaderboard
     * @return List of PlayerScore objects sorted by score (highest first)
     */
    public List<PlayerScore> getSortedLeaderboard() {
        List<PlayerScore> leaderboard = new ArrayList<>();
        for (Map.Entry<String, Float> entry : playerScores.entrySet()) {
            leaderboard.add(new PlayerScore(entry.getKey(), entry.getValue()));
        }
        leaderboard.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return leaderboard;
    }
    
    /**
     * Represents a player's score for leaderboard display
     */
    public static class PlayerScore {
        private String userId;
        private float score;
        
        public PlayerScore(String userId, float score) {
            this.userId = userId;
            this.score = score;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public float getScore() {
            return score;
        }
    }
}
