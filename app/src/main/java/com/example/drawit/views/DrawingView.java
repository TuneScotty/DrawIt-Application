package com.example.drawit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

public class DrawingView extends View {
    private Path path;
    private Paint paint;
    private ArrayList<Path> paths;
    private ArrayList<Paint> paints;
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;
    private boolean isErasing = false;
    private int currentColor = Color.BLACK;
    private float currentSize = 12f;

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupDrawing();
    }

    private void setupDrawing() {
        paths = new ArrayList<>();
        paints = new ArrayList<>();
        path = new Path();
        paint = new Paint();
        
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(12);
    }

    public void setColor(int color) {
        currentColor = color;
        isErasing = false;
        paint.setColor(color);
        paint.setStrokeWidth(currentSize);
    }

    public void setBrushSize(float size) {
        currentSize = size;
        paint.setStrokeWidth(size);
        if (!isErasing) {
            paint.setColor(currentColor);
        }
    }

    public void setEraser() {
        isErasing = true;
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(currentSize * 2); // Make eraser slightly bigger
    }

    public void clearCanvas() {
        paths.clear();
        paints.clear();
        isErasing = false;
        paint.setColor(currentColor);
        paint.setStrokeWidth(currentSize);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        for (int i = 0; i < paths.size(); i++) {
            canvas.drawPath(paths.get(i), paints.get(i));
        }
        canvas.drawPath(path, paint);
    }

    private void touchStart(float x, float y) {
        path = new Path();
        paths.add(path);
        paints.add(new Paint(paint));
        path.reset();
        path.moveTo(x, y);
        mX = x;
        mY = y;
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            path.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }

    private void touchUp() {
        path.lineTo(mX, mY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touchUp();
                invalidate();
                break;
        }
        return true;
    }
} 