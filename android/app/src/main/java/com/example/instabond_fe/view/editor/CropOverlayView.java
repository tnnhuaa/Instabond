package com.example.instabond_fe.view.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CropOverlayView extends View {

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cropRect = new RectF();

    private float targetRatio;

    public CropOverlayView(Context context) {
        this(context, null);
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dimPaint.setColor(0x4D000000);
        dimPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2f));

        gridPaint.setColor(0x66FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(dp(4f));
    }

    public void setCropRatio(float ratio) {
        targetRatio = ratio;
        invalidate();
    }

    public RectF getCropRect() {
        ensureCropRect(getWidth(), getHeight());
        return new RectF(cropRect);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        ensureCropRect(getWidth(), getHeight());

        canvas.drawRect(0, 0, getWidth(), cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, dimPaint);

        canvas.drawRect(cropRect, borderPaint);

        float thirdWidth = cropRect.width() / 3f;
        float thirdHeight = cropRect.height() / 3f;
        for (int i = 1; i <= 2; i++) {
            float x = cropRect.left + (thirdWidth * i);
            float y = cropRect.top + (thirdHeight * i);
            canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint);
            canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint);
        }

        drawCorner(canvas, cropRect.left, cropRect.top, 1, 1);
        drawCorner(canvas, cropRect.right, cropRect.top, -1, 1);
        drawCorner(canvas, cropRect.left, cropRect.bottom, 1, -1);
        drawCorner(canvas, cropRect.right, cropRect.bottom, -1, -1);
    }

    private void ensureCropRect(int width, int height) {
        float horizontalInset = width * 0.1f;
        float verticalInset = height * 0.1f;
        float availableWidth = width - (horizontalInset * 2f);
        float availableHeight = height - (verticalInset * 2f);

        float rectWidth = availableWidth;
        float rectHeight = availableHeight;

        if (targetRatio > 0f) {
            float availableRatio = availableWidth / availableHeight;
            if (availableRatio > targetRatio) {
                rectHeight = availableHeight;
                rectWidth = rectHeight * targetRatio;
            } else {
                rectWidth = availableWidth;
                rectHeight = rectWidth / targetRatio;
            }
        }

        float left = (width - rectWidth) / 2f;
        float top = (height - rectHeight) / 2f;
        cropRect.set(left, top, left + rectWidth, top + rectHeight);
    }

    private void drawCorner(Canvas canvas, float x, float y, int horizontalDirection, int verticalDirection) {
        float length = dp(24f);
        canvas.drawLine(x, y, x + (length * horizontalDirection), y, handlePaint);
        canvas.drawLine(x, y, x, y + (length * verticalDirection), handlePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
