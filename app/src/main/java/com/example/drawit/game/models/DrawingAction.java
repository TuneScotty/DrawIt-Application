package com.example.drawit.game.models;

import android.graphics.Color;

/**
 * Represents a drawing action performed by the drawer.
 * This model is used to synchronize drawing operations across clients.
 */
public class DrawingAction {
    // Action types
    public static final int ACTION_DRAW = 0;
    public static final int ACTION_MOVE = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_UNDO = 3;
    public static final int ACTION_FILL = 4;
    
    private int actionType;
    private float startX;
    private float startY;
    private float endX;
    private float endY;
    private int color;
    private float strokeWidth;
    private long timestamp;
    private String drawerId;
    
    /**
     * Default constructor for Firebase
     */
    public DrawingAction() {
        this.timestamp = System.currentTimeMillis();
        this.color = Color.BLACK;
        this.strokeWidth = 5.0f;
    }
    
    /**
     * Constructor for drawing or moving actions
     */
    public DrawingAction(int actionType, float startX, float startY, float endX, float endY, 
                         int color, float strokeWidth, String drawerId) {
        this();
        this.actionType = actionType;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.drawerId = drawerId;
    }
    
    /**
     * Constructor for clear, undo, or fill actions
     */
    public DrawingAction(int actionType, String drawerId) {
        this();
        this.actionType = actionType;
        this.drawerId = drawerId;
    }
    
    /**
     * Constructor for fill action at specific coordinates
     */
    public DrawingAction(int actionType, float startX, float startY, int color, String drawerId) {
        this();
        this.actionType = actionType;
        this.startX = startX;
        this.startY = startY;
        this.color = color;
        this.drawerId = drawerId;
    }
    
    // Getters and setters
    public int getActionType() {
        return actionType;
    }
    
    public void setActionType(int actionType) {
        this.actionType = actionType;
    }
    
    public float getStartX() {
        return startX;
    }
    
    public void setStartX(float startX) {
        this.startX = startX;
    }
    
    public float getStartY() {
        return startY;
    }
    
    public void setStartY(float startY) {
        this.startY = startY;
    }
    
    public float getEndX() {
        return endX;
    }
    
    public void setEndX(float endX) {
        this.endX = endX;
    }
    
    public float getEndY() {
        return endY;
    }
    
    public void setEndY(float endY) {
        this.endY = endY;
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
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getDrawerId() {
        return drawerId;
    }
    
    public void setDrawerId(String drawerId) {
        this.drawerId = drawerId;
    }
}
