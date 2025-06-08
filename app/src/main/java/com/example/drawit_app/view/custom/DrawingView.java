package com.example.drawit_app.view.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.example.drawit_app.model.Drawing;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for drawing functionality
 * Handles touch events and renders paths with specified colors and stroke widths
 */
public class DrawingView extends View {

    private static final float DEFAULT_STROKE_WIDTH = 8f;
    private static final int DEFAULT_COLOR = Color.BLACK;
    
    private Bitmap canvasBitmap;
    private Canvas drawCanvas;
    private Paint drawPaint, canvasPaint;
    private Path currentPath;
    private float currentX, currentY;
    
    // Store all paths for undo/redo and for converting to Drawing model
    private final List<PathInfo> paths = new ArrayList<>();
    private int currentColor = DEFAULT_COLOR;
    private float currentStrokeWidth = DEFAULT_STROKE_WIDTH;
    
    // Advanced drawing features
    public enum BrushType {
        NORMAL,      // Standard brush
        CALLIGRAPHY, // Angle-sensitive brush
        AIRBRUSH,    // Soft edges
        MARKER       // Thick with opacity
    }
    
    private BrushType currentBrushType = BrushType.NORMAL;
    private float currentPressure = 1.0f; // Default pressure
    private boolean pressureSensitivityEnabled = false;
    
    // Listener for path completion
    private OnPathCompletedListener pathCompletedListener;
    
    public DrawingView(Context context) {
        super(context);
        init();
    }
    
    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DrawingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Setup drawing tools
        drawPaint = new Paint();
        setupPaint();
        
        currentPath = new Path();
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }
    
    private void setupPaint() {
        drawPaint.setColor(currentColor);
        drawPaint.setAntiAlias(true);
        
        // Apply pressure sensitivity if enabled
        float effectiveStrokeWidth = pressureSensitivityEnabled ? 
                currentStrokeWidth * currentPressure : currentStrokeWidth;
        
        // Configure paint based on brush type
        switch (currentBrushType) {
            case NORMAL:
                drawPaint.setStrokeWidth(effectiveStrokeWidth);
                drawPaint.setStyle(Paint.Style.STROKE);
                drawPaint.setStrokeJoin(Paint.Join.ROUND);
                drawPaint.setStrokeCap(Paint.Cap.ROUND);
                drawPaint.setMaskFilter(null);
                drawPaint.setAlpha(255);
                break;
                
            case CALLIGRAPHY:
                drawPaint.setStrokeWidth(effectiveStrokeWidth);
                drawPaint.setStyle(Paint.Style.STROKE);
                drawPaint.setStrokeJoin(Paint.Join.BEVEL); // Sharp corners
                drawPaint.setStrokeCap(Paint.Cap.SQUARE); // Flat ends
                drawPaint.setMaskFilter(null);
                drawPaint.setAlpha(255);
                break;
                
            case AIRBRUSH:
                drawPaint.setStrokeWidth(effectiveStrokeWidth * 1.5f); // Wider stroke
                drawPaint.setStyle(Paint.Style.STROKE);
                drawPaint.setStrokeJoin(Paint.Join.ROUND);
                drawPaint.setStrokeCap(Paint.Cap.ROUND);
                // Blur effect for soft edges
                drawPaint.setMaskFilter(new BlurMaskFilter(effectiveStrokeWidth / 2, BlurMaskFilter.Blur.NORMAL));
                drawPaint.setAlpha(200); // Slightly transparent
                break;
                
            case MARKER:
                drawPaint.setStrokeWidth(effectiveStrokeWidth * 2f); // Much wider stroke
                drawPaint.setStyle(Paint.Style.STROKE);
                drawPaint.setStrokeJoin(Paint.Join.ROUND);
                drawPaint.setStrokeCap(Paint.Cap.SQUARE); // Flat end like a marker
                drawPaint.setMaskFilter(null);
                drawPaint.setAlpha(180); // More transparent
                break;
        }
        
        Log.d("DrawingView", "Paint configured: brush=" + currentBrushType + 
              ", width=" + effectiveStrokeWidth + ", color=" + currentColor);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // According to StackOverflow solution, we need to ensure dimensions are valid
        // and postpone bitmap creation if they're not
        if (w <= 0 || h <= 0) {
            Log.e("DrawingView", "Invalid dimensions in onSizeChanged: w=" + w + ", h=" + h);
            // Don't try to create a bitmap with invalid dimensions
            return;
        }
        
        // Log the valid dimensions we're using
        Log.d("DrawingView", "Creating bitmap with dimensions: w=" + w + ", h=" + h);
        
        // Recycle old bitmap to prevent memory leaks
        if (canvasBitmap != null && !canvasBitmap.isRecycled()) {
            canvasBitmap.recycle();
            canvasBitmap = null;
        }
        
        try {
            // Create new bitmap and canvas with valid dimensions
            canvasBitmap = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
            drawCanvas = new Canvas(canvasBitmap);
            
            // Draw existing paths onto the new canvas
            redrawCanvas();
            
            Log.d("DrawingView", "Successfully created new bitmap with dimensions: w=" + w + ", h=" + h);
        } catch (OutOfMemoryError e) {
            Log.e("DrawingView", "Out of memory creating bitmap: " + e.getMessage());
            // Handle out of memory by using a smaller bitmap or clearing paths
            paths.clear();
            System.gc(); // Request garbage collection
        } catch (Exception e) {
            Log.e("DrawingView", "Error creating bitmap: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas == null) {
            Log.e("DrawingView", "Cannot draw - canvas is null");
            return;
        }
        
        // Always draw a white background
        canvas.drawColor(Color.WHITE);
        
        // Check if we need to create or recreate the bitmap
        if (canvasBitmap == null || canvasBitmap.isRecycled()) {
            // Get dimensions - following StackOverflow solution to ensure they're valid
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                try {
                    Log.d("DrawingView", "Creating bitmap in onDraw: w=" + w + ", h=" + h);
                    canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    drawCanvas = new Canvas(canvasBitmap);
                    
                    // If we have paths, redraw them on the new canvas
                    if (!paths.isEmpty()) {
                        redrawCanvas();
                    }
                } catch (OutOfMemoryError oom) {
                    Log.e("DrawingView", "Out of memory creating bitmap in onDraw: " + oom.getMessage());
                    // Clear paths to recover memory
                    paths.clear();
                    System.gc();
                } catch (Exception ex) {
                    Log.e("DrawingView", "Failed to create bitmap in onDraw: " + ex.getMessage());
                }
            } else {
                Log.e("DrawingView", "Cannot create bitmap - invalid dimensions: w=" + w + ", h=" + h);
                // Just draw paths on the provided canvas without a backing bitmap
                drawPathsDirectly(canvas);
                return;
            }
        }
        
        // Draw the bitmap if it exists and is valid
        if (canvasBitmap != null && !canvasBitmap.isRecycled()) {
            try {
                canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
            } catch (Exception e) {
                Log.e("DrawingView", "Error drawing bitmap: " + e.getMessage());
                // If we can't draw the bitmap, draw paths directly
                drawPathsDirectly(canvas);
            }
        } else {
            // If bitmap is invalid, draw paths directly
            drawPathsDirectly(canvas);
        }
    }
    
    /**
     * Draw paths directly on the provided canvas without using a backing bitmap
     * This is a fallback method when the bitmap is not available
     */
    private void drawPathsDirectly(Canvas canvas) {
        if (canvas == null) {
            return;
        }
        
        Log.d("DrawingView", "Drawing paths directly on canvas (bitmap unavailable)");
        
        // Draw all stored paths
        for (PathInfo pathInfo : paths) {
            drawPaint.setColor(pathInfo.color);
            drawPaint.setStrokeWidth(pathInfo.strokeWidth);
            canvas.drawPath(pathInfo.path, drawPaint);
        }
        
        // Draw current path
        drawPaint.setColor(currentColor);
        drawPaint.setStrokeWidth(currentStrokeWidth);
        canvas.drawPath(currentPath, drawPaint);
        
        // Schedule another redraw to ensure continuous updates
        invalidate();
    }
    
    // For improved touch handling and smoother drawing
    private static final float TOUCH_TOLERANCE = 4f;
    private static final int VELOCITY_FILTER_WEIGHT = 2;
    private float lastVelocity;
    private float lastWidth;
    private float lastX, lastY;
    private boolean isDrawing = false;
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If drawing is disabled, don't process touch events
        if (!isEnabled()) {
            return false;
        }
        
        float x = event.getX();
        float y = event.getY();
        
        // Check for pressure sensitivity support
        if (pressureSensitivityEnabled && event.getDevice() != null) {
            // Get pressure if available, otherwise use default
            if (event.getDevice().getSources() == InputDevice.SOURCE_TOUCHSCREEN) {
                float pressure = event.getPressure();
                // Only update if we have a valid pressure reading
                if (pressure > 0) {
                    currentPressure = pressure;
                    // Update paint with new pressure
                    setupPaint();
                    Log.d("DrawingView", "Pressure detected: " + pressure);
                }
            }
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start new path with improved handling
                isDrawing = true;
                touchStart(x, y);
                invalidate();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                // Add path segment with improved smoothing
                if (isDrawing) {
                    touchMove(x, y);
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                // Finish path with improved handling
                if (isDrawing) {
                    touchUp(x, y);
                    isDrawing = false;
                    invalidate();
                }
                
                // Reset pressure to default after drawing
                if (pressureSensitivityEnabled) {
                    currentPressure = 1.0f;
                    setupPaint();
                }
                
                // Call performClick for accessibility
                performClick();
                return true;
                
            default:
                return false;
        }
    }
    
    private void touchStart(float x, float y) {
        // Reset path and store starting point
        currentPath = new Path();
        currentPath.moveTo(x, y);
        
        // Store points for velocity calculation
        currentX = x;
        currentY = y;
        lastX = x;
        lastY = y;
        
        // Reset velocity tracking
        lastVelocity = 0;
        lastWidth = currentStrokeWidth;
        
        Log.d("DrawingView", "Started drawing at x=" + x + ", y=" + y);
    }
    
    private void touchMove(float x, float y) {
        float dx = Math.abs(x - currentX);
        float dy = Math.abs(y - currentY);
        
        // Only process if movement is significant
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            // Calculate velocity for dynamic stroke width (optional feature)
            float velocity = (float) Math.sqrt(dx * dx + dy * dy);
            velocity = VELOCITY_FILTER_WEIGHT * velocity + (1 - VELOCITY_FILTER_WEIGHT) * lastVelocity;
            
            // Use quadratic Bezier for smoother curves
            // The midpoint becomes the end point of the curve, and the current point is the control point
            currentPath.quadTo(currentX, currentY, (x + currentX) / 2, (y + currentY) / 2);
            
            // Update points for next iteration
            lastX = currentX;
            lastY = currentY;
            currentX = x;
            currentY = y;
            lastVelocity = velocity;
            
            // For debugging
            if (Math.random() < 0.01) { // Log only 1% of points to avoid spam
                Log.d("DrawingView", "Drawing curve to x=" + x + ", y=" + y);
            }
        }
    }
    
    private void touchUp(float x, float y) {
        // Connect the final point
        currentPath.lineTo(x, y);
        
        // Draw the path to the canvas
        if (drawCanvas != null) {
            drawCanvas.drawPath(currentPath, drawPaint);
        } else {
            Log.e("DrawingView", "Cannot draw - drawCanvas is null");
        }
        
        // Store the completed path
        PathInfo pathInfo = new PathInfo(new Path(currentPath), currentColor, currentStrokeWidth);
        paths.add(pathInfo);
        
        // Notify listener for real-time updates
        if (pathCompletedListener != null) {
            pathCompletedListener.onPathCompleted(pathInfo);
        }
        
        // Update paths JSON for network sync
        if (pathUpdateListener != null) {
            pathUpdateListener.onPathUpdated(getPathsAsJson());
        }
        
        // Reset current path
        currentPath.reset();
        
        Log.d("DrawingView", "Finished drawing path, total paths: " + paths.size());
    }
    
    @Override
    public boolean performClick() {
        // Call the super implementation, which generates an AccessibilityEvent
        // and calls the onClick() listener on the view, if any
        super.performClick();
        return true;
    }
    
    /**
     * Set the current drawing color
     * @param color ARGB color value
     */
    public void setColor(@ColorInt int color) {
        currentColor = color;
        setupPaint();
    }
    
    /**
     * Set the brush type for drawing
     * @param brushType The brush type to use
     */
    public void setBrushType(BrushType brushType) {
        this.currentBrushType = brushType;
        setupPaint();
        Log.d("DrawingView", "Brush type changed to: " + brushType);
    }
    
    /**
     * Get the current brush type
     * @return Current brush type
     */
    public BrushType getCurrentBrushType() {
        return currentBrushType;
    }
    
    /**
     * Enable or disable pressure sensitivity
     * @param enabled True to enable pressure sensitivity, false to disable
     */
    public void setPressureSensitivityEnabled(boolean enabled) {
        this.pressureSensitivityEnabled = enabled;
        Log.d("DrawingView", "Pressure sensitivity " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if pressure sensitivity is enabled
     * @return True if pressure sensitivity is enabled
     */
    public boolean isPressureSensitivityEnabled() {
        return pressureSensitivityEnabled;
    }
    
    /**
     * Enable or disable touch interaction for drawing
     * This is used to allow or prevent drawing based on game state
     * @param enabled true to enable drawing, false to disable
     */
    public void setInteractionEnabled(boolean enabled) {
        // Store enabled state
        setClickable(enabled);
        setFocusable(enabled);
    }
    
    /**
     * Set the current stroke width
     * @param width Width in pixels
     */
    public void setStrokeWidth(float width) {
        currentStrokeWidth = width;
        setupPaint();
    }
    
    /**
     * Clear the canvas and all stored paths
     */
    /**
     * Clears all drawing paths and the canvas
     * Handles bitmap errors gracefully to prevent crashes
     */
    public void clearCanvas() {
        // Clear all stored paths
        paths.clear();
        currentPath.reset();
        
        // Clear the canvas if it's valid
        if (drawCanvas != null && canvasBitmap != null && !canvasBitmap.isRecycled()) {
            try {
                drawCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                Log.d("DrawingView", "Canvas cleared successfully");
            } catch (Exception e) {
                Log.e("DrawingView", "Error clearing canvas: " + e.getMessage());
                
                // Try to recreate the bitmap if clearing failed
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    try {
                        // Recycle old bitmap
                        if (canvasBitmap != null && !canvasBitmap.isRecycled()) {
                            canvasBitmap.recycle();
                        }
                        
                        // Create new bitmap
                        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        drawCanvas = new Canvas(canvasBitmap);
                        Log.d("DrawingView", "Recreated bitmap after clear error");
                    } catch (Exception ex) {
                        Log.e("DrawingView", "Failed to recreate bitmap: " + ex.getMessage());
                    }
                }
            }
        } else {
            Log.d("DrawingView", "Skipped canvas clear - invalid bitmap or canvas");
        }
        
        // Request a UI update
        invalidate();
    }
    
    /**
     * Undo the last drawn path
     * @return true if undo was successful, false if no paths to undo
     */
    public boolean undo() {
        if (paths.size() > 0) {
            paths.remove(paths.size() - 1);
            redrawCanvas();
            return true;
        }
        return false;
    }
    
    /**
     * Redraw all paths on the canvas
     */
    /**
     * Redraws all paths onto the canvas bitmap
     * Handles errors gracefully to prevent crashes
     */
    private void redrawCanvas() {
        // Check if we have a valid canvas to draw on
        if (drawCanvas == null || canvasBitmap == null || canvasBitmap.isRecycled()) {
            Log.e("DrawingView", "Cannot redraw - canvas or bitmap is invalid");
            invalidate(); // Request a redraw using onDraw which will handle this case
            return;
        }
        
        try {
            // Clear the canvas
            drawCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            
            // Draw all stored paths
            for (PathInfo pathInfo : paths) {
                drawPaint.setColor(pathInfo.color);
                drawPaint.setStrokeWidth(pathInfo.strokeWidth);
                drawCanvas.drawPath(pathInfo.path, drawPaint);
            }
            
            Log.d("DrawingView", "Successfully redrew " + paths.size() + " paths on canvas");
        } catch (Exception e) {
            Log.e("DrawingView", "Error redrawing canvas: " + e.getMessage());
        }
        
        // Request a UI update
        invalidate();
    }
    
    /**
     * Set a listener for path completion events
     * @param listener Listener to set
     */
    public void setOnPathCompletedListener(OnPathCompletedListener listener) {
        this.pathCompletedListener = listener;
    }
    
    /**
     * Convert the current drawing to a Drawing model object
     * @param drawingId ID for the drawing
     * @param userId ID of the user creating the drawing
     * @param word The word being drawn
     * @return Drawing model object
     */
    public Drawing toDrawingModel(String drawingId, String userId, String word) {
        Drawing drawing = new Drawing(drawingId, userId, null, 0, word);
        
        for (PathInfo pathInfo : paths) {
            Drawing.DrawingPath drawingPath = new Drawing.DrawingPath(pathInfo.color, pathInfo.strokeWidth);
            
            // Approximate the path as a series of points
            // This is a simplified approach - in a real app, you might want more precise path conversion
            float[] coordinates = new float[2];
            android.graphics.PathMeasure measure = new android.graphics.PathMeasure(pathInfo.path, false);
            float length = measure.getLength();
            float distance = 0f;
            float step = 5f; // Distance between points
            
            while (distance < length) {
                measure.getPosTan(distance, coordinates, null);
                drawingPath.addPoint(coordinates[0], coordinates[1]);
                distance += step;
            }
            
            drawing.addPath(drawingPath);
        }
        
        return drawing;
    }
    
    /**
     * Represents a single path with its properties
     */
    public static class PathInfo {
        public final Path path;
        public final int color;
        public final float strokeWidth;
        
        public PathInfo(Path path, int color, float strokeWidth) {
            this.path = path;
            this.color = color;
            this.strokeWidth = strokeWidth;
        }
    }
    
    /**
     * Listener interface for path completion events
     */
    public interface OnPathCompletedListener {
        void onPathCompleted(PathInfo pathInfo);
    }
    
    /**
     * Interface for path update events for real-time drawing sync
     */
    public interface OnPathUpdateListener {
        void onPathUpdated(String pathsJson);
    }
    
    // For real-time path updates during multiplayer drawing
    private OnPathUpdateListener pathUpdateListener;
    
    /**
     * Set a listener for path updates (for real-time drawing sync)
     * @param listener Listener to be notified of path updates
     */
    public void setOnPathUpdateListener(OnPathUpdateListener listener) {
        this.pathUpdateListener = listener;
    }
    
    /**
     * Clear all paths and reset the drawing
     */
    public void clearDrawing() {
        clearCanvas();
        
        // Notify listener if present
        if (pathUpdateListener != null) {
            pathUpdateListener.onPathUpdated(getPathsAsJson());
        }
    }
    
    /**
     * Get the current drawing as a bitmap
     * @return Bitmap representation of the drawing
     */
    public Bitmap getBitmap() {
        // Create a new bitmap with the same dimensions
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw the view onto the bitmap
        draw(canvas);
        
        return bitmap;
    }
    
    /**
     * Convert all paths to a JSON string for network transmission
     * @return JSON string representing all paths
     */
    public String getPathsAsJson() {
        try {
            JSONArray jsonPaths = new JSONArray();
            
            for (PathInfo pathInfo : paths) {
                JSONObject jsonPath = new JSONObject();
                jsonPath.put("color", pathInfo.color);
                jsonPath.put("strokeWidth", pathInfo.strokeWidth);
                
                // Convert path to a series of points
                JSONArray points = new JSONArray();
                float[] coordinates = new float[2];
                android.graphics.PathMeasure measure = new android.graphics.PathMeasure(pathInfo.path, false);
                float length = measure.getLength();
                float distance = 0f;
                float step = 5f;
                
                while (distance < length) {
                    measure.getPosTan(distance, coordinates, null);
                    
                    JSONObject point = new JSONObject();
                    point.put("x", coordinates[0]);
                    point.put("y", coordinates[1]);
                    points.put(point);
                    
                    distance += step;
                }
                
                jsonPath.put("points", points);
                jsonPaths.put(jsonPath);
            }
            
            JSONObject jsonDrawing = new JSONObject();
            jsonDrawing.put("paths", jsonPaths);
            
            return jsonDrawing.toString();
            
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    /**
     * Recreate paths from a JSON string (received from network)
     * @param jsonString JSON string representing paths
     */
    public void setPathsFromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            Log.d("DrawingView", "Empty JSON string for paths, skipping");
            return;
        }
        
        // Ensure we have a valid bitmap and canvas before proceeding
        if (canvasBitmap == null || drawCanvas == null) {
            Log.w("DrawingView", "Canvas bitmap or drawCanvas is null, attempting to create");
            int w = getWidth();
            int h = getHeight();
            
            if (w <= 0 || h <= 0) {
                // If dimensions are not available yet, post to UI thread to try again later
                Log.w("DrawingView", "Invalid dimensions for bitmap creation: w=" + w + ", h=" + h);
                post(() -> setPathsFromJson(jsonString));
                return;
            }
            
            try {
                canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                drawCanvas = new Canvas(canvasBitmap);
                Log.d("DrawingView", "Created new bitmap in setPathsFromJson: w=" + w + ", h=" + h);
            } catch (Exception e) {
                Log.e("DrawingView", "Failed to create bitmap in setPathsFromJson: " + e.getMessage());
                return;
            }
        }
        
        try {
            Log.d("DrawingView", "Parsing JSON paths: " + jsonString.substring(0, Math.min(50, jsonString.length())) + "...");
            JSONObject jsonDrawing = new JSONObject(jsonString);
            JSONArray jsonPaths = jsonDrawing.getJSONArray("paths");
            
            // Clear existing paths
            paths.clear();
            Log.d("DrawingView", "Cleared existing paths, loading " + jsonPaths.length() + " new paths");
            
            // Recreate paths from JSON
            for (int i = 0; i < jsonPaths.length(); i++) {
                JSONObject jsonPath = jsonPaths.getJSONObject(i);
                int color = jsonPath.getInt("color");
                float strokeWidth = (float) jsonPath.getDouble("strokeWidth");
                
                Path path = new Path();
                JSONArray points = jsonPath.getJSONArray("points");
                
                if (points.length() > 0) {
                    JSONObject firstPoint = points.getJSONObject(0);
                    float x = (float) firstPoint.getDouble("x");
                    float y = (float) firstPoint.getDouble("y");
                    path.moveTo(x, y);
                    
                    for (int j = 1; j < points.length(); j++) {
                        JSONObject point = points.getJSONObject(j);
                        x = (float) point.getDouble("x");
                        y = (float) point.getDouble("y");
                        path.lineTo(x, y);
                    }
                }
                
                PathInfo pathInfo = new PathInfo(path, color, strokeWidth);
                paths.add(pathInfo);
            }
            
            // Redraw canvas with new paths
            redrawCanvas();
            Log.d("DrawingView", "Successfully loaded and drew " + paths.size() + " paths");
            
        } catch (JSONException e) {
            Log.e("DrawingView", "Error parsing JSON paths: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.e("DrawingView", "Unexpected error in setPathsFromJson: " + e.getMessage());
            e.printStackTrace();
        }
    }
}