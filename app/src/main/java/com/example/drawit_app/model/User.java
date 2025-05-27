package com.example.drawit_app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * User entity representing a player in the DrawIt game.
 * Stores authentication and profile information.
 */
@Entity(tableName = "users")
public class User {
    
    @PrimaryKey
    @NonNull
    private String userId;
    
    private String username;
    private String avatarUrl;
    private String authToken;
    private String email;
    private int totalGamesPlayed;
    private int gamesWon;
    private float averageRating;
    private boolean ready; // Indicates if user is ready to start the game
    
    // Required empty constructor for Room
    public User() {}
    
    @Ignore
    public User(@NonNull String userId, String username, String avatarUrl) {
        this.userId = userId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.totalGamesPlayed = 0;
        this.averageRating = 0.0f;
    }
    
    @NonNull
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    /**
     * Check if user is ready to start the game
     * @return True if ready, false otherwise
     */
    public boolean isReady() {
        return ready;
    }
    
    /**
     * Set user ready state
     * @param ready True if ready, false otherwise
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }
    
    public String getAuthToken() {
        return authToken;
    }
    
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }
    
    public void setTotalGamesPlayed(int totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }
    
    public float getAverageRating() {
        return averageRating;
    }
    
    public void setAverageRating(float averageRating) {
        this.averageRating = averageRating;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public int getGamesWon() {
        return gamesWon;
    }
    
    public void setGamesWon(int gamesWon) {
        this.gamesWon = gamesWon;
    }
    
    /**
     * Alias for getTotalGamesPlayed for API compatibility
     */
    public int getGamesPlayed() {
        return getTotalGamesPlayed();
    }
    
    /**
     * Updates the average rating based on a new game's rating
     * @param newRating Rating from the latest game
     */
    public void updateAverageRating(float newRating) {
        if (totalGamesPlayed == 0) {
            averageRating = newRating;
        } else {
            float totalRating = averageRating * totalGamesPlayed;
            totalRating += newRating;
            totalGamesPlayed++;
            averageRating = totalRating / totalGamesPlayed;
        }
    }
}
