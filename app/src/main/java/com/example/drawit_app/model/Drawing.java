package com.example.drawit_app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.drawit_app.data.DrawingPathsConverter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Represents a drawing created by a player in a game round.
 */
@Entity(tableName = "drawings")
@TypeConverters(DrawingPathsConverter.class)
public class Drawing {
    
    @PrimaryKey
    @NonNull
    private String drawingId;
    
    private String userId;
    private String gameId;
    private int roundNumber;
    private String word;
    private Date timestamp;
    private float averageRating;
    private int ratingCount;
    private int userRating; // Rating by the current user
    
    // The actual drawing data
    private List<DrawingPath> paths;
    
    // Transient fields not stored in database
    private transient User artist; // User who created this drawing
    private transient String imagePath; // JSON representation of the drawing for rendering
    
    public Drawing() {
        this.paths = new ArrayList<>();
    }
    
    @Ignore
    public Drawing(@NonNull String drawingId, String userId, String gameId, int roundNumber, String word) {
        this.drawingId = drawingId;
        this.userId = userId;
        this.gameId = gameId;
        this.roundNumber = roundNumber;
        this.word = word;
        this.timestamp = new Date();
        this.averageRating = 0.0f;
        this.ratingCount = 0;
        this.paths = new ArrayList<>();
    }
    
    @NonNull
    public String getDrawingId() {
        return drawingId;
    }
    
    public void setDrawingId(@NonNull String drawingId) {
        this.drawingId = drawingId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public int getRoundNumber() {
        return roundNumber;
    }
    
    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }
    
    public String getWord() {
        return word;
    }
    
    public void setWord(String word) {
        this.word = word;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public float getAverageRating() {
        return averageRating;
    }
    
    public void setAverageRating(float averageRating) {
        this.averageRating = averageRating;
    }
    
    /**
     * Get the rating given by the current user
     * @return Rating value (1-5) or 0 if not rated
     */
    public int getUserRating() {
        return userRating;
    }
    
    /**
     * Set the rating given by the current user
     * @param userRating Rating value (1-5)
     */
    public void setUserRating(int userRating) {
        this.userRating = userRating;
    }
    
    /**
     * Get the user who created this drawing
     * @return User object of the artist
     */
    public User getArtist() {
        if (artist == null) {
            // Create a placeholder if artist is not set
            artist = new User();
            artist.setUserId(userId);
            artist.setUsername("Artist " + userId);
        }
        return artist;
    }
    
    /**
     * Set the user who created this drawing
     * @param artist User object
     */
    public void setArtist(User artist) {
        this.artist = artist;
    }
    
    /**
     * Get the creation date of the drawing
     * @return Date object
     */
    public Date getCreatedAt() {
        return timestamp;
    }
    
    /**
     * Get the JSON representation of the drawing paths
     * @return JSON string or null if not set
     */
    public String getImagePath() {
        return imagePath;
    }
    
    /**
     * Set the JSON representation of the drawing paths
     * @param imagePath JSON string
     */
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    
    /**
     * Get the unique ID of this drawing
     * @return Drawing ID
     */
    public String getId() {
        return drawingId;
    }
    
    public int getRatingCount() {
        return ratingCount;
    }
    
    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }
    
    public List<DrawingPath> getPaths() {
        return paths;
    }
    
    public void setPaths(List<DrawingPath> paths) {
        this.paths = paths;
    }
    
    /**
     * Adds a new path to the drawing
     * @param path Path to add
     */
    public void addPath(DrawingPath path) {
        paths.add(path);
    }
    
    /**
     * Updates the average rating with a new rating
     * @param rating New rating (1-5)
     */
    public void addRating(float rating) {
        if (rating < 1 || rating > 5) {
            return; // Invalid rating
        }
        
        float totalScore = averageRating * ratingCount;
        totalScore += rating;
        ratingCount++;
        averageRating = totalScore / ratingCount;
    }
    
    /**
     * Represents a path in a drawing with color, width, and points
     */
    public static class DrawingPath {
        private int color;
        private float strokeWidth;
        private List<PointF> points;
        
        public DrawingPath() {
            this.points = new ArrayList<>();
        }
        
        public DrawingPath(int color, float strokeWidth) {
            this.color = color;
            this.strokeWidth = strokeWidth;
            this.points = new ArrayList<>();
        }
        
        public int getColor() {
            return color;
        }
        
        public void setColor(int color) {
            this.color = color;
        }
        
        public float getStrokeWidth() {
            return strokeWidth;
        }
        
        public void setStrokeWidth(float strokeWidth) {
            this.strokeWidth = strokeWidth;
        }
        
        public List<PointF> getPoints() {
            return points;
        }
        
        public void setPoints(List<PointF> points) {
            this.points = points;
        }
        
        public void addPoint(float x, float y) {
            points.add(new PointF(x, y));
        }
    }
    
    /**
     * Simple class to represent a point in a drawing
     */
    public static class PointF {
        private float x;
        private float y;
        
        public PointF() {}
        
        public PointF(float x, float y) {
            this.x = x;
            this.y = y;
        }
        
        public float getX() {
            return x;
        }
        
        public void setX(float x) {
            this.x = x;
        }
        
        public float getY() {
            return y;
        }
        
        public void setY(float y) {
            this.y = y;
        }
    }
}
