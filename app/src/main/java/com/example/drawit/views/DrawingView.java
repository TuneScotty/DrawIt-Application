package com.example.drawit.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.util.Log;

import com.example.drawit.game.models.Stroke;
import com.example.drawit.game.models.DrawingAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom view for drawing functionality in the DrawIt game.
 * Implements a stroke-based rendering model for improved performance and stability.
 */
public class DrawingView extends View {
    private static final String TAG = "DrawingView";
    
    // Drawing path for current stroke
    private Path drawPath;
    
    // Drawing paint
    private Paint drawPaint;
    
    // Canvas paint
    private Paint canvasPaint;
    
    // Initial color
    private int paintColor = Color.BLACK;
    
    // Canvas
    private Canvas drawCanvas;
    
    // Canvas bitmap
    private Bitmap drawBitmap;
    
    // Brush sizes
    private float brushSize = 12f;
    private float lastBrushSize = 12f;
    
    // Enabled/disabled state
    private boolean isEnabled = true;
    
    // View-only mode (no drawing allowed)
    private boolean viewOnlyMode = false;
    
    // Erase flag
    private boolean erase = false;
    
    // Store all strokes for persistent rendering
    private List<com.example.drawit.game.models.Stroke> strokes = new ArrayList<>();
    
    // Current stroke being drawn
    private com.example.drawit.game.models.Stroke currentStroke;
    
    // User ID for the current drawer
    private String currentUserId;
    
    // Cache for storing processed drawings
    private LruCache<String, Bitmap> drawingCache;
    
    // Flag to indicate if we're currently drawing
    private boolean isDrawing = false;
    
    // Minimum distance between points in pixels
    private static final float MIN_DISTANCE_BETWEEN_POINTS = 5.0f;
    
    // Last point added
    private float lastX = -1;
    private float lastY = -1;
    
    // Drawing listeners
    private OnStrokeListener strokeListener;
    private OnDrawingActionListener drawingActionListener;
    private DrawingListener drawingListener;
    
    // Tool state
    private Tool currentTool = Tool.BRUSH;
    
    // Stroke history for undo/redo
    private List<List<com.example.drawit.game.models.Stroke>> strokeHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;
    
    // Flag to track if the canvas has been modified
    private boolean isModified = false;
    
    // Interface for stroke events
    public interface OnStrokeListener {
        void onStrokeCompleted(com.example.drawit.game.models.Stroke stroke);
    }
    
    // Interface for drawing action events
    public interface OnDrawingActionListener {
        void onDrawingAction(com.example.drawit.game.models.DrawingAction action);
    }
    
    public interface DrawingListener {
        void onDrawingAction(com.example.drawit.game.models.DrawingAction action);
    }
    
    public enum Tool {
        BRUSH,
        ERASER
    }
    
    /**
     * Save the current drawing state to history for undo/redo
     */
    private void saveToHistory() {
        try {
            // Don't save if there are no strokes
            if (strokes.isEmpty()) {
                return;
            }
            
            // Remove any redo history after the current position
            if (currentHistoryIndex < strokeHistory.size() - 1) {
                strokeHistory = new ArrayList<>(strokeHistory.subList(0, currentHistoryIndex + 1));
            }
            
            // Create a deep copy of the current strokes
            List<Stroke> copy = new ArrayList<>();
            for (Stroke stroke : strokes) {
                copy.add(new Stroke(stroke.getUserId(), stroke.getColor(), stroke.getWidth()));
            }
            
            // Add to history
            strokeHistory.add(copy);
            currentHistoryIndex = strokeHistory.size() - 1;
            
            // Limit history size to prevent memory issues
            final int MAX_HISTORY_SIZE = 20;
            if (strokeHistory.size() > MAX_HISTORY_SIZE) {
                strokeHistory.remove(0);
                currentHistoryIndex--;
            }
            
            // Mark as modified
            isModified = true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving to history", e);
        }
    }
    
    /**
     * Undo the last drawing action
     */
    public void undo() {
        try {
            if (canUndo()) {
                // Save current state before undoing
                if (currentHistoryIndex == strokeHistory.size() - 1) {
                    saveToHistory();
                    currentHistoryIndex--; // Move back to the previous state
                }
                
                // Load the previous state
                loadFromHistory(currentHistoryIndex);
                currentHistoryIndex--;
                
                // Redraw the view
                invalidate();
                
                // Mark as modified
                isModified = true;
                
                Log.d(TAG, "Undo action performed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing undo", e);
        }
    }
    
    /**
     * Redo the last undone action
     */
    public void redo() {
        try {
            if (canRedo()) {
                // Move to the next state in history
                currentHistoryIndex++;
                
                // Load the next state
                loadFromHistory(currentHistoryIndex);
                
                // Redraw the view
                invalidate();
                
                // Mark as modified
                isModified = true;
                
                Log.d(TAG, "Redo action performed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing redo", e);
        }
    }
    
    /**
     * Check if an undo operation is possible
     * @return true if undo is possible, false otherwise
     */
    public boolean canUndo() {
        return currentHistoryIndex >= 0 && !strokeHistory.isEmpty();
    }
    
    /**
     * Check if a redo operation is possible
     * @return true if redo is possible, false otherwise
     */
    public boolean canRedo() {
        return currentHistoryIndex < strokeHistory.size() - 1 && !strokeHistory.isEmpty();
    }
    
    /**
     * Load a specific state from history
     * @param index The index of the state to load
     */
    private void loadFromHistory(int index) {
        try {
            if (index >= 0 && index < strokeHistory.size()) {
                // Clear current strokes
                strokes.clear();
                
                // Add all strokes from the history
                for (Stroke stroke : strokeHistory.get(index)) {
                    strokes.add(new Stroke(stroke.getUserId(), stroke.getColor(), stroke.getWidth()));
                }
                
                Log.d(TAG, "Loaded state from history at index: " + index);
                
                // Reset the draw path
                drawPath = new Path();
                currentStroke = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading from history", e);
        }
    }
    
    /**
     * Check if the canvas has been modified
     * @return true if modified, false otherwise
     */
    public boolean isModified() {
        return isModified;
    }
    
    /**
     * Reset the modified flag
     */
    public void resetModifiedFlag() {
        isModified = false;
    }
    
    /**
     * Get the current tool
     * @return The current tool (BRUSH or ERASER)
     */
    public Tool getCurrentTool() {
        return currentTool;
    }
    
    /**
     * Get the current brush size
     * @return The current brush size in pixels
     */
    public float getBrushSize() {
        return brushSize;
    }
    
    /**
     * Get the current color
     * @return The current color as an ARGB int
     */
    public int getColor() {
        return paintColor;
    }
    
    /**
     * Get the current strokes as a list
     * @return A list of all strokes on the canvas
     */
    public List<com.example.drawit.game.models.Stroke> getStrokes() {
        return new ArrayList<>(strokes);
    }
    
    /**
     * Set the strokes from a list
     * @param strokes The list of strokes to set
     */
    public void setStrokes(List<com.example.drawit.game.models.Stroke> strokes) {
        this.strokes = new ArrayList<>(strokes);
        invalidate();
    }
    
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
        drawPath = new Path();

        // Setup drawing paint
        drawPaint = new Paint();
        drawPaint.setAntiAlias(true);
        drawPaint.setDither(true);
        drawPaint.setColor(paintColor);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        drawPaint.setStrokeWidth(brushSize);

        // Setup canvas paint
        canvasPaint = new Paint(Paint.DITHER_FLAG);

        // Initialize drawing cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        drawingCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    /**
     * Set eraser mode
     * @param isErase true to enable eraser, false to disable
     */
    public void setEraser(boolean isErase) {
        try {
            erase = isErase;
            if (erase) {
                currentTool = Tool.ERASER;
                drawPaint.setColor(Color.WHITE);
                drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                // Notify listeners of the tool change
                if (drawingActionListener != null) {
                    com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                    action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_SET_ERASER);
                    action.setBrushSize(brushSize);
                    drawingActionListener.onDrawingAction(action);
                }
            } else {
                currentTool = Tool.BRUSH;
                drawPaint.setXfermode(null);
                drawPaint.setColor(paintColor);

                // Notify listeners of the tool change
                if (drawingActionListener != null) {
                    com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                    action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_SET_BRUSH);
                    action.setColor(paintColor);
                    action.setBrushSize(brushSize);
                    drawingActionListener.onDrawingAction(action);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting eraser mode", e);
        }
    }

    /**
     * Set the drawing color
     * @param color The color to set (as an ARGB int)
     */
    /**
     * Set eraser mode
     * @param isEraser True to enable eraser mode, false to disable
     */
    public void setEraseMode(boolean isEraser) {
        this.erase = isEraser;
        if (isEraser) {
            // Set up eraser paint properties
            drawPaint.setColor(Color.WHITE);
            drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        } else {
            // Restore normal paint properties
            drawPaint.setColor(paintColor);
            drawPaint.setXfermode(null);
        }
    }
    
    public void setColor(int color) {
        try {
            paintColor = color;
            drawPaint.setColor(paintColor);
            // Explicitly disable eraser mode when setting a color
            erase = false;
            drawPaint.setXfermode(null);

            // If we're currently drawing, update the current stroke color
            if (currentStroke != null) {
                currentStroke.setColor(paintColor);
            }

            // Notify listeners of the color change
            if (drawingActionListener != null) {
                com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_SET_COLOR);
                action.setColor(paintColor);
                drawingActionListener.onDrawingAction(action);
            }
            
            Log.d(TAG, "Color set to: " + Integer.toHexString(paintColor));
        } catch (Exception e) {
            Log.e(TAG, "Error setting color", e);
        }
    }

    /**
     * Set the brush size
     * @param size The brush size in pixels
     */
    public void setBrushSize(float size) {
        try {
            // Save the last non-erase brush size
            if (!erase) {
                lastBrushSize = brushSize;
            }

            brushSize = size;
            drawPaint.setStrokeWidth(brushSize);

            // If we're currently drawing, update the current stroke width
            if (currentStroke != null) {
                currentStroke.setWidth(brushSize);
            }

            // Notify listeners of the brush size change
            if (drawingActionListener != null) {
                com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_SET_BRUSH_SIZE);
                action.setBrushSize(brushSize);
                drawingActionListener.onDrawingAction(action);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting brush size", e);
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw the bitmap containing all committed strokes
        if (drawBitmap != null && !drawBitmap.isRecycled()) {
            canvas.drawBitmap(drawBitmap, 0, 0, canvasPaint);
        } else if (getWidth() > 0 && getHeight() > 0) {
            // If bitmap is null or recycled, recreate it
            drawBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            drawCanvas = new Canvas(drawBitmap);
            drawCanvas.drawColor(Color.WHITE);
            drawAllStrokes(); // Redraw all strokes
            invalidate(); // Request another draw pass
            return;
        }
        
        // Draw the current path being created
        if (isDrawing && drawPath != null) {
            canvas.drawPath(drawPath, drawPaint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If disabled or in view-only mode, don't respond to touch events
        if (!isEnabled() || viewOnlyMode) return false;
        
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start a new stroke
                touchStart(x, y);
                break;
                
            case MotionEvent.ACTION_MOVE:
                // Continue the current stroke
                touchMove(x, y);
                break;
                
            case MotionEvent.ACTION_UP:
                // Finish the current stroke
                touchUp();
                break;
                
            default:
                return false;
        }
        
        invalidate();
        return true;
    }
    
    /**
     * Handle the start of a touch event
     */
    private void touchStart(float x, float y) {
        // Reset the drawing path
        drawPath = new Path();
        drawPath.moveTo(x, y);
        
        // Create a new stroke
        currentStroke = new com.example.drawit.game.models.Stroke(currentUserId, drawPaint.getColor(), drawPaint.getStrokeWidth());
        currentStroke.addPoint(x, y);
        
        // Save the current position
        lastX = x;
        lastY = y;
        
        // Set drawing flag
        isDrawing = true;
        
        if (drawingListener != null) {
            com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
            action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_START);
            action.setStartX(x);
            action.setStartY(y);
            drawingListener.onDrawingAction(action);
        }
    }
    
    /**
     * Handle touch movement
     */
    private void touchMove(float x, float y) {
        // Calculate distance from last point
        float dx = Math.abs(x - lastX);
        float dy = Math.abs(y - lastY);
        
        // Only add points if we've moved far enough
        if (dx >= MIN_DISTANCE_BETWEEN_POINTS || dy >= MIN_DISTANCE_BETWEEN_POINTS) {
            // Add a quadratic bezier from the last point to the current point
            drawPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
            
            // Add the point to the current stroke
            if (currentStroke != null) {
                currentStroke.addPoint(x, y);
            }
            
            // Update last position
            lastX = x;
            lastY = y;
            
            if (drawingListener != null) {
                com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_DRAW);
                action.setStartX(x);
                action.setStartY(y);
                drawingListener.onDrawingAction(action);
            }
        }
    }
    
    /**
     * Handle the end of a touch event
     */
    private void touchUp() {
        // Complete the path
        drawPath.lineTo(lastX, lastY);
        
        // Add the stroke to our list
        if (currentStroke != null && currentStroke.getPoints().size() > 0) {
            strokes.add(new Stroke(currentStroke.getUserId(), currentStroke.getColor(), currentStroke.getWidth()));
            
            // Draw the completed stroke to the canvas
            drawStroke(currentStroke, drawCanvas);
            
            // Notify stroke listener
            if (strokeListener != null) {
                strokeListener.onStrokeCompleted(currentStroke);
            }
            
            // Notify drawing action listener
            if (drawingActionListener != null) {
                com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                action.setType(com.example.drawit.game.models.DrawingAction.TYPE_STROKE);
                action.setStroke(currentStroke);
                action.setTimestamp(System.currentTimeMillis());
                drawingActionListener.onDrawingAction(action);
            }
            
            if (drawingListener != null) {
                com.example.drawit.game.models.DrawingAction action = new com.example.drawit.game.models.DrawingAction();
                action.setActionType(com.example.drawit.game.models.DrawingAction.ACTION_END);
                action.setStartX(lastX);
                action.setStartY(lastY);
                drawingListener.onDrawingAction(action);
            }
        }
        
        // Reset path and drawing flag
        drawPath = new Path();
        isDrawing = false;
        currentStroke = null;
    }
    
    /**
     * Draw a single stroke on the specified canvas
     */
    private void drawStroke(com.example.drawit.game.models.Stroke stroke, Canvas canvas) {
        if (stroke == null || stroke.getPoints() == null || stroke.getPoints().isEmpty() || canvas == null) {
            return;
        }
        
        Paint paint = new Paint(drawPaint);
        paint.setColor(stroke.getColor());
        paint.setStrokeWidth(stroke.getWidth());
        
        Path path = new Path();
        List<com.example.drawit.game.models.Stroke.Point> points = stroke.getPoints();
        
        // Start the path at the first point
        com.example.drawit.game.models.Stroke.Point firstPoint = points.get(0);
        path.moveTo(firstPoint.getX(), firstPoint.getY());
        
        // If there's only one point, draw it as a dot
        if (points.size() == 1) {
            canvas.drawPoint(firstPoint.getX(), firstPoint.getY(), paint);
            return;
        }
        
        // Connect subsequent points with quadratic bezier curves for smoothness
        for (int i = 1; i < points.size(); i++) {
            com.example.drawit.game.models.Stroke.Point current = points.get(i);
            
            if (i < points.size() - 1) {
                // For smoother curves, use quadratic bezier
                com.example.drawit.game.models.Stroke.Point next = points.get(i+1);
                float midX = (current.getX() + next.getX()) / 2;
                float midY = (current.getY() + next.getY()) / 2;
                
                path.quadTo(current.getX(), current.getY(), midX, midY);
            } else {
                // For the last point, just draw a line
                path.lineTo(current.getX(), current.getY());
            }
        }
        
        canvas.drawPath(path, paint);
    }
    
    /**
     * Draw all strokes on the canvas
     */
    private void drawAllStrokes() {
        if (drawCanvas == null || strokes.isEmpty()) return;
        
        drawCanvas.drawColor(Color.WHITE); // Clear canvas first
        
        for (com.example.drawit.game.models.Stroke stroke : strokes) {
            drawStroke(stroke, drawCanvas);
        }
        
        invalidate();
    }
    
    /**
     * Add a stroke received from another client
     */
    public void addStroke(com.example.drawit.game.models.Stroke stroke) {
        if (stroke == null) return;
        
        // Skip if this stroke was drawn by the current user (already in our list)
        if (currentUserId != null && currentUserId.equals(stroke.getUserId())) {
            // Check if we already have this stroke (by ID)
            for (com.example.drawit.game.models.Stroke existingStroke : strokes) {
                if (existingStroke.getStrokeId().equals(stroke.getStrokeId())) {
                    Log.d(TAG, "Skipping duplicate stroke: " + stroke.getStrokeId());
                    return;
                }
            }
        }
        
        // Add to our list of strokes
        strokes.add(new Stroke(stroke.getUserId(), stroke.getColor(), stroke.getWidth()));
        
        // Draw the stroke to the canvas
        if (drawCanvas != null) {
            drawStroke(stroke, drawCanvas);
            invalidate();
        }
        
        Log.d(TAG, "Added stroke from user: " + stroke.getUserId() + 
              " with " + (stroke.getPoints() != null ? stroke.getPoints().size() : 0) + " points");
    }
    
    /**
     * Load a complete drawing from a list of drawing actions
     * This is used when loading drawings from Firebase
     * @param actions The list of drawing actions to load
     * @param playerId The player ID for caching purposes
     */
    public void loadDrawingFromActions(List<com.example.drawit.game.models.DrawingAction> actions, String playerId) {
        if (actions == null || actions.isEmpty()) {
            clearCanvas();
            return;
        }
        
        // Sort actions by timestamp to ensure correct drawing order
        Collections.sort(actions, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        
        // Extract strokes from actions
        List<com.example.drawit.game.models.Stroke> strokeList = new ArrayList<>();
        for (com.example.drawit.game.models.DrawingAction action : actions) {
            if (action.getActionType() == com.example.drawit.game.models.DrawingAction.TYPE_STROKE && action.getStroke() != null) {
                strokeList.add(action.getStroke());
            } else if (action.getActionType() == com.example.drawit.game.models.DrawingAction.TYPE_CLEAR) {
                // Clear all previous strokes if a clear action is encountered
                strokeList.clear();
            }
        }
        
        // Load the strokes
        loadDrawingFromStrokes(strokeList, playerId);
    }
    
    /**
     * Load a complete drawing from a list of strokes
     * This is used when loading drawings from Firebase
     * @param strokeList The list of strokes to load
     * @param playerId The player ID for caching purposes
     */
    public void loadDrawingFromStrokes(List<com.example.drawit.game.models.Stroke> strokeList, String playerId) {
        if (strokeList == null) {
            clearCanvas();
            return;
        }
        
        // Check if we have a cached bitmap for this player's drawing
        if (playerId != null) {
            final String cacheKey = playerId + "_" + (strokeList != null ? strokeList.size() : 0);
            Bitmap cachedBitmap = drawingCache.get(cacheKey);
            
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                Log.d(TAG, "Using cached bitmap for player: " + playerId);
                
                // Use the cached bitmap
                if (drawCanvas != null) {
                    drawCanvas.drawColor(Color.WHITE); // Clear first
                    drawCanvas.drawBitmap(cachedBitmap, 0, 0, null);
                    invalidate();
                }
                return;
            }
        }
        
        // Clear existing strokes
        strokes.clear();
        
        // Add all new strokes
        if (strokeList != null && !strokeList.isEmpty()) {
            strokes.addAll(strokeList);
        }
        
        // Redraw all strokes
        drawAllStrokes();
        
        // Cache the result if we have a player ID
        if (playerId != null && drawBitmap != null && !drawBitmap.isRecycled()) {
            final String cacheKey = playerId + "_" + (strokeList != null ? strokeList.size() : 0);
            
            // Create a copy of the bitmap for caching
            Bitmap cacheBitmap = drawBitmap.copy(drawBitmap.getConfig(), false);
            drawingCache.put(cacheKey, cacheBitmap);
            
            Log.d(TAG, "Cached bitmap for player: " + playerId);
        }
    }
    
    /**
     * Draw from a given drawing action
     * @param action The drawing action to process
     */
    public void drawFromAction(com.example.drawit.game.models.DrawingAction action) {
        if (action == null) return;
        
        switch (action.getActionType()) {
            case com.example.drawit.game.models.DrawingAction.TYPE_STROKE:
                com.example.drawit.game.models.Stroke stroke = action.getStroke();
                if (stroke != null) {
                    addStroke(stroke);
                }
                break;
            case com.example.drawit.game.models.DrawingAction.TYPE_CLEAR:
                clearCanvas();
                break;
            default:
                Log.w(TAG, "Unhandled drawing action type: " + action.getActionType());
        }
    }
    
    /**
     * Clear the canvas and reset the drawing state
     */
    public void clearCanvas() {
        try {
            // Save the current state to history before clearing
            saveToHistory();
            
            // Clear all strokes
            strokes.clear();
            
            // Reset the current stroke and path
            currentStroke = null;
            drawPath = new Path();
            
            // Clear the canvas bitmap if it exists
            if (drawBitmap != null) {
                drawBitmap.eraseColor(Color.TRANSPARENT);
                drawCanvas.drawColor(Color.WHITE);
            }
            
            // Redraw the view
            invalidate();
            
            // Mark as modified
            isModified = true;
            
            // Notify listeners that the canvas was cleared
            if (drawingActionListener != null) {
                DrawingAction action = new DrawingAction();
                action.setActionType(com.example.drawit.game.models.DrawingAction.TYPE_CLEAR);
                action.setTimestamp(System.currentTimeMillis());
                drawingActionListener.onDrawingAction(action);
            }
            
            Log.d(TAG, "Canvas cleared");
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing canvas", e);
        }
    }
    
    private void addDrawingAction(float x, float y, int actionType) {
        if (drawingActionListener != null) {
            DrawingAction action = new DrawingAction();
            action.setDrawerId(currentUserId);
            action.setStartX(x);
            action.setStartY(y);
            action.setActionType(actionType);
            drawingActionListener.onDrawingAction(action);
        }
    }
    
    /**
     * Set view-only mode (no drawing allowed)
     * @param viewOnly true to enable view-only mode, false to allow drawing
     */
    public void setViewOnlyMode(boolean viewOnly) {
        this.viewOnlyMode = viewOnly;
    }
    
    /**
     * Check if view-only mode is enabled
     * @return true if view-only mode is enabled
     */
    public boolean isViewOnlyMode() {
        return viewOnlyMode;
    }
    
    /**
     * Load and display a list of drawing actions from the server
     * @param drawingActions The list of drawing actions to display
     */
    public void loadDrawingActions(List<DrawingAction> drawingActions) {
        if (drawingActions == null || drawingActions.isEmpty()) {
            Log.d(TAG, "No drawing actions to load");
            return;
        }
        
        // Clear the canvas first
        clearCanvas();
        
        // Process each drawing action in order
        for (DrawingAction action : drawingActions) {
            drawFromAction(action);
        }
        
        // Force a redraw
        invalidate();
        
        Log.d(TAG, "Loaded " + drawingActions.size() + " drawing actions");
    }
}
