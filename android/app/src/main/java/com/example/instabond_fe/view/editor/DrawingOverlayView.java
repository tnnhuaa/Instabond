package com.example.instabond_fe.view.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DrawingOverlayView extends View {

    private final List<Stroke> strokes = new ArrayList<>();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path drawPath = new Path();

    private Stroke activeStroke;
    private boolean drawingEnabled;
    private int brushColor = 0xFF8037B1;
    private float brushWidthPx;

    public DrawingOverlayView(Context context) {
        this(context, null);
    }

    public DrawingOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setAntiAlias(true);
        brushWidthPx = dp(10f);
    }

    public void setDrawingEnabled(boolean enabled) {
        drawingEnabled = enabled;
    }

    public void setBrushColor(int color) {
        brushColor = color;
    }

    public void setBrushWidthPx(float widthPx) {
        brushWidthPx = Math.max(dp(2f), widthPx);
    }

    public void clear() {
        strokes.clear();
        activeStroke = null;
        invalidate();
    }

    public boolean hasStrokes() {
        return !strokes.isEmpty();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!drawingEnabled || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }

        float normalizedX = clamp(event.getX() / getWidth());
        float normalizedY = clamp(event.getY() / getHeight());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeStroke = new Stroke(brushColor, brushWidthPx / Math.min(getWidth(), getHeight()));
                activeStroke.points.add(new PointF(normalizedX, normalizedY));
                strokes.add(activeStroke);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeStroke == null) {
                    return false;
                }
                activeStroke.points.add(new PointF(normalizedX, normalizedY));
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (activeStroke != null) {
                    activeStroke.points.add(new PointF(normalizedX, normalizedY));
                    activeStroke = null;
                    invalidate();
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        renderStrokes(canvas, getWidth(), getHeight());
    }

    public void renderToCanvas(Canvas canvas, int outWidth, int outHeight) {
        renderStrokes(canvas, outWidth, outHeight);
    }

    private void renderStrokes(Canvas canvas, int outWidth, int outHeight) {
        if (outWidth <= 0 || outHeight <= 0) {
            return;
        }

        float minDimension = Math.min(outWidth, outHeight);
        for (Stroke stroke : strokes) {
            if (stroke.points.isEmpty()) {
                continue;
            }

            strokePaint.setColor(stroke.color);
            strokePaint.setStrokeWidth(Math.max(2f, stroke.widthFraction * minDimension));
            drawPath.reset();

            PointF first = stroke.points.get(0);
            drawPath.moveTo(first.x * outWidth, first.y * outHeight);
            for (int i = 1; i < stroke.points.size(); i++) {
                PointF point = stroke.points.get(i);
                drawPath.lineTo(point.x * outWidth, point.y * outHeight);
            }
            canvas.drawPath(drawPath, strokePaint);
        }
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static class Stroke {
        final int color;
        final float widthFraction;
        final List<PointF> points = new ArrayList<>();

        Stroke(int color, float widthFraction) {
            this.color = color;
            this.widthFraction = widthFraction;
        }
    }
}
