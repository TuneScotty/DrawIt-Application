package com.example.drawit.game.models;

import android.graphics.Color;

import java.util.UUID;

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
    public static final int TYPE_STROKE = 5; // New type for stroke-based model
    public static final int TYPE_CLEAR = 6;  // New type for clearing canvas
    public static final int ACTION_START = 7; // Start of a stroke
    public static final int ACTION_END = 8;   // End of a stroke
    public static final int ACTION_SET_COLOR = 9; // Set color action
    public static final int ACTION_SET_BRUSH_SIZE = 10; // Set brush size action
    public static final int ACTION_SET_ERASER = 11; // Set eraser action
    public static final int ACTION_SET_BRUSH = 12; // Set brush action
    
    private int actionType;
    private float startX;
    private float startY;
    private float endX;
    private float endY;
    private int color;
    private float strokeWidth;
    private long timestamp;
    private String drawerId;
    private String actionId;
    private Stroke stroke;
    
    /**
     * Default constructor for Firebase
     */
    public DrawingAction() {
        this.timestamp = System.currentTimeMillis();
        this.color = Color.BLACK;
        this.strokeWidth = 5.0f;
        this.actionId = UUID.randomUUID().toString();
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
    
    /**
     * @deprecated Use setActionType() instead
     */
    @Deprecated
    public void setType(int type) {
        this.actionType = type;
    }
    
    public void setBrushSize(float size) {
        this.strokeWidth = size;
    }
    
    public float getBrushSize() {
        return strokeWidth;
    }
    
    public void setColor(int color) {
        this.color = color;
    }
    
    public int getColor() {
        return color;
    }
    
    public void setStroke(Stroke stroke) {
        this.stroke = stroke;
    }
    
    public Stroke getStroke() {
        return stroke;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getDrawerId() {
        return drawerId;
    }
    
    public void setDrawerId(String drawerId) {
        this.drawerId = drawerId;
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
    
    public float getStrokeWidth() {
        return strokeWidth;
    }
    
    public void setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
    }
    
    public String getActionId() {
        return actionId;
    }
    
    public void setActionId(String actionId) {
        this.actionId = actionId;
    }
    
    @Override
    public String toString() {
        return "DrawingAction{" +
                "actionType=" + actionType +
                ", startX=" + startX +
                ", startY=" + startY +
                ", endX=" + endX +
                ", endY=" + endY +
                ", color=" + color +
                ", strokeWidth=" + strokeWidth +
                ", timestamp=" + timestamp +
                ", drawerId='" + drawerId + '\'' +
                ", actionId='" + actionId + '\'' +
                ", stroke=" + stroke +
                '}';
    }
}
