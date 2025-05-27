package com.example.drawit_app.model;

/**
 * Represents a player's score in a game for leaderboard display
 */
public class PlayerScore {
    
    private User player;
    private int score;
    private int correctGuesses;
    private int drawingRatings;
    private int totalRatingPoints;
    private boolean guessedCurrentRound;
    
    public PlayerScore() {
        this.correctGuesses = 0;
        this.drawingRatings = 0;
        this.totalRatingPoints = 0;
        this.guessedCurrentRound = false;
    }
    
    public PlayerScore(User player, int score) {
        this.player = player;
        this.score = score;
        this.correctGuesses = 0;
        this.drawingRatings = 0;
        this.totalRatingPoints = 0;
        this.guessedCurrentRound = false;
    }
    
    public User getPlayer() {
        return player;
    }
    
    public void setPlayer(User player) {
        this.player = player;
    }
    
    public int getScore() {
        return score;
    }
    
    public void setScore(int score) {
        this.score = score;
    }
    
    public int getCorrectGuesses() {
        return correctGuesses;
    }
    
    public void setCorrectGuesses(int correctGuesses) {
        this.correctGuesses = correctGuesses;
    }
    
    public void incrementCorrectGuesses() {
        this.correctGuesses++;
    }
    
    public int getDrawingRatings() {
        return drawingRatings;
    }
    
    public void setDrawingRatings(int drawingRatings) {
        this.drawingRatings = drawingRatings;
    }
    
    public int getTotalRatingPoints() {
        return totalRatingPoints;
    }
    
    public void setTotalRatingPoints(int totalRatingPoints) {
        this.totalRatingPoints = totalRatingPoints;
    }
    
    public void addRating(int rating) {
        this.totalRatingPoints += rating;
        this.drawingRatings++;
    }
    
    public float getAverageRating() {
        if (drawingRatings == 0) return 0;
        return (float) totalRatingPoints / drawingRatings;
    }
    
    public boolean hasGuessedCurrentRound() {
        return guessedCurrentRound;
    }
    
    public void setGuessedCurrentRound(boolean guessedCurrentRound) {
        this.guessedCurrentRound = guessedCurrentRound;
    }
    
    /**
     * Add points to the player's score
     * @param points Number of points to add
     */
    public void addPoints(int points) {
        this.score += points;
    }
}
