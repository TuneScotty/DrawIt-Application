package com.example.drawit.game.models;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete drawing stroke performed by a player.
 * This model is used for efficient stroke-based drawing synchronization.
 */
public class Stroke {
    private String strokeId;
    private String userId;
    private int color;
    private float width;
    private List<Point> points;
    private long timestamp;

    /**
     * Default constructor for Firebase
     */
    public Stroke() {
        this.strokeId = UUID.randomUUID().toString();
        this.color = Color.BLACK;
        this.width = 5.0f;
        this.points = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with all parameters
     */
    public Stroke(String userId, int color, float width) {
        this();
        this.userId = userId;
        this.color = color;
        this.width = width;
    }

    // Getters and setters
    public String getStrokeId() {
        return strokeId;
    }

    public void setStrokeId(String strokeId) {
        this.strokeId = strokeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public void addPoint(float x, float y) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(new Point(x, y));
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Nested Point class to represent x,y coordinates
     */
    public static class Point {
        private float x;
        private float y;

        // Default constructor for Firebase
        public Point() {
        }

        public Point(float x, float y) {
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
