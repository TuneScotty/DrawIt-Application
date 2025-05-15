package com.example.drawit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.drawit.game.models.DrawingAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for drawing functionality in the DrawIt game.
 * Handles user drawing input and synchronizes with other clients.
 */
public class DrawingView extends View {
    // Drawing state
    private Path currentPath;
    private Paint currentPaint;
    private List<Path> paths = new ArrayList<>();
    private List<Paint> paints = new ArrayList<>();
    
    // Touch tracking
    private float lastX, lastY;
    private static final float TOUCH_TOLERANCE = 4;
    
    // Drawing properties
    private boolean isErasing = false;
    private int currentColor = Color.BLACK;
    private float currentBrushSize = 12f;
    
    // Action listener
    private OnDrawingActionListener actionListener;

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialize the drawing view with default settings
     */
    private void init() {
        currentPath = new Path();
        currentPaint = createPaint(currentColor, currentBrushSize);
        setBackgroundColor(Color.WHITE);
    }

    /**
     * Create a new Paint object with specified properties
     */
    private Paint createPaint(int color, float strokeWidth) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    /**
     * Set the current drawing color
     */
    public void setColor(int color) {
        currentColor = color;
        isErasing = false;
        currentPaint = createPaint(color, currentBrushSize);
    }

    /**
     * Set the current brush size
     */
    public void setBrushSize(float size) {
        currentBrushSize = size;
        currentPaint = createPaint(isErasing ? Color.WHITE : currentColor, size);
    }

    /**
     * Enable eraser mode
     */
    public void setEraser() {
        isErasing = true;
        currentPaint = createPaint(Color.WHITE, currentBrushSize * 2); // Make eraser slightly bigger
    }

    /**
     * Clear the entire canvas
     */
    public void clearCanvas() {
        paths.clear();
        paints.clear();
        currentPath = new Path();
        isErasing = false;
        currentPaint = createPaint(currentColor, currentBrushSize);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw all saved paths
        for (int i = 0; i < paths.size(); i++) {
            canvas.drawPath(paths.get(i), paints.get(i));
        }
        
        // Draw current path
        canvas.drawPath(currentPath, currentPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start a new path
                touchStart(x, y);
                notifyDrawingAction(DrawingAction.ACTION_DRAW, x, y);
                break;
                
            case MotionEvent.ACTION_MOVE:
                // Continue the current path
                touchMove(x, y);
                notifyDrawingAction(DrawingAction.ACTION_MOVE, x, y);
                break;
                
            case MotionEvent.ACTION_UP:
                // Finish the current path
                touchUp();
                break;
        }
        
        invalidate();
        return true;
    }
    
    /**
     * Handle the start of a touch event
     */
    private void touchStart(float x, float y) {
        // Reset and start a new path
        currentPath = new Path();
        currentPath.moveTo(x, y);
        
        // Save the current position
        lastX = x;
        lastY = y;
        
        // Add to the list of paths
        paths.add(currentPath);
        paints.add(new Paint(currentPaint));
    }
    
    /**
     * Handle touch movement
     */
    private void touchMove(float x, float y) {
        float dx = Math.abs(x - lastX);
        float dy = Math.abs(y - lastY);
        
        // Only register movement if it's beyond the tolerance threshold
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            // Create a smooth curve through the points
            currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
            
            // Update the last position
            lastX = x;
            lastY = y;
        }
    }
    
    /**
     * Handle the end of a touch event
     */
    private void touchUp() {
        // Complete the path by drawing a line to the last point
        currentPath.lineTo(lastX, lastY);
    }
    
    /**
     * Notify the listener of a drawing action
     */
    private void notifyDrawingAction(int actionType, float x, float y) {
        if (actionListener == null) return;
        
        DrawingAction action = new DrawingAction();
        action.setActionType(actionType);
        action.setStartX(x);
        action.setStartY(y);
        action.setEndX(lastX);
        action.setEndY(lastY);
        action.setColor(isErasing ? Color.WHITE : currentColor);
        action.setStrokeWidth(currentBrushSize);
        action.setTimestamp(System.currentTimeMillis());
        
        actionListener.onDrawingAction(action);
    }
    
    /**
     * Apply a drawing action received from another client
     */
    public void drawFromAction(DrawingAction action) {
        if (action == null) return;
        
        switch (action.getActionType()) {
            case DrawingAction.ACTION_DRAW:
                // Start a new path
                Path newPath = new Path();
                newPath.moveTo(action.getStartX(), action.getStartY());
                
                Paint newPaint = createPaint(action.getColor(), action.getStrokeWidth());
                
                paths.add(newPath);
                paints.add(newPaint);
                break;
                
            case DrawingAction.ACTION_MOVE:
                // Continue the current path if we have one
                if (!paths.isEmpty()) {
                    Path path = paths.get(paths.size() - 1);
                    path.lineTo(action.getStartX(), action.getStartY());
                }
                break;
                
            case DrawingAction.ACTION_CLEAR:
                // Clear the canvas
                clearCanvas();
                break;
        }
        
        invalidate();
    }
    
    /**
     * Interface for drawing action callbacks
     */
    public interface OnDrawingActionListener {
        void onDrawingAction(DrawingAction action);
    }
    
    /**
     * Set the drawing action listener
     */
    public void setOnDrawingActionListener(OnDrawingActionListener listener) {
        this.actionListener = listener;
    }
}