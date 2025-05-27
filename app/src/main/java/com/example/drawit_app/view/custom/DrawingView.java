package com.example.drawit_app.view.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
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
    private Paint canvasPaint;
    private Paint drawPaint;
    private Path currentPath;
    private float currentX, currentY;
    
    // Store all paths for undo/redo and for converting to Drawing model
    private final List<PathInfo> paths = new ArrayList<>();
    private int currentColor = DEFAULT_COLOR;
    private float currentStrokeWidth = DEFAULT_STROKE_WIDTH;
    
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
        drawPaint.setStrokeWidth(currentStrokeWidth);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Create new bitmap and canvas with new dimensions
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // Draw background bitmap
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        
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
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start new path
                currentPath = new Path();
                currentPath.moveTo(x, y);
                currentX = x;
                currentY = y;
                invalidate();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                // Add path segment
                float dx = Math.abs(x - currentX);
                float dy = Math.abs(y - currentY);
                
                if (dx >= 4 || dy >= 4) {
                    // Use quadTo for smoother curves
                    currentPath.quadTo(currentX, currentY, (x + currentX) / 2, (y + currentY) / 2);
                    currentX = x;
                    currentY = y;
                }
                invalidate();
                return true;
                
            case MotionEvent.ACTION_UP:
                // Finish path
                currentPath.lineTo(x, y);
                drawCanvas.drawPath(currentPath, drawPaint);
                
                // Store the completed path
                PathInfo pathInfo = new PathInfo(new Path(currentPath), currentColor, currentStrokeWidth);
                paths.add(pathInfo);
                
                // Notify listener
                if (pathCompletedListener != null) {
                    pathCompletedListener.onPathCompleted(pathInfo);
                }
                
                // Reset current path
                currentPath.reset();
                invalidate();
                
                // Call performClick for accessibility
                performClick();
                return true;
                
            default:
                return false;
        }
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
    public void clearCanvas() {
        paths.clear();
        drawCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
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
    private void redrawCanvas() {
        drawCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
        
        for (PathInfo pathInfo : paths) {
            drawPaint.setColor(pathInfo.color);
            drawPaint.setStrokeWidth(pathInfo.strokeWidth);
            drawCanvas.drawPath(pathInfo.path, drawPaint);
        }
        
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
            return;
        }
        
        try {
            JSONObject jsonDrawing = new JSONObject(jsonString);
            JSONArray jsonPaths = jsonDrawing.getJSONArray("paths");
            
            // Clear existing paths
            paths.clear();
            
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
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
