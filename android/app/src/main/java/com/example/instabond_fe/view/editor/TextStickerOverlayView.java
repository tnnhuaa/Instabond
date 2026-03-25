package com.example.instabond_fe.view.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextStickerOverlayView extends View {

    public enum StickerStyle {
        SCRIPT,
        BOLD,
        OUTLINE
    }

    private final List<TextSticker> stickers = new ArrayList<>();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect measureBounds = new Rect();

    private boolean editingEnabled;
    private int selectedIndex = -1;
    private float lastTouchX;
    private float lastTouchY;

    public TextStickerOverlayView(Context context) {
        this(context, null);
    }

    public TextStickerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        textPaint.setTextAlign(Paint.Align.CENTER);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(3f));
        outlinePaint.setColor(Color.WHITE);
        outlinePaint.setTextAlign(Paint.Align.CENTER);

        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(1.5f));
        selectionPaint.setColor(0x99FFFFFF);
    }

    public void setEditingEnabled(boolean enabled) {
        editingEnabled = enabled;
        invalidate();
    }

    public boolean hasSelection() {
        return selectedIndex >= 0 && selectedIndex < stickers.size();
    }

    public void addSticker(String text, int color, float sizeFraction, StickerStyle style) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        TextSticker sticker = new TextSticker();
        sticker.text = text.trim();
        sticker.color = color;
        sticker.sizeFraction = sizeFraction;
        sticker.centerXFraction = 0.5f;
        sticker.centerYFraction = 0.3f + (stickers.size() * 0.12f);
        sticker.style = style;
        stickers.add(sticker);
        selectedIndex = stickers.size() - 1;
        invalidate();
    }

    public void updateSelectedText(String text) {
        TextSticker selected = getSelectedSticker();
        if (selected == null || text == null || text.trim().isEmpty()) {
            return;
        }
        selected.text = text.trim();
        invalidate();
    }

    public void updateSelectedColor(int color) {
        TextSticker selected = getSelectedSticker();
        if (selected == null) {
            return;
        }
        selected.color = color;
        invalidate();
    }

    public void updateSelectedSizeFraction(float sizeFraction) {
        TextSticker selected = getSelectedSticker();
        if (selected == null) {
            return;
        }
        selected.sizeFraction = sizeFraction;
        invalidate();
    }

    public void updateSelectedStyle(StickerStyle style) {
        TextSticker selected = getSelectedSticker();
        if (selected == null || style == null) {
            return;
        }
        selected.style = style;
        invalidate();
    }

    public void removeSelectedSticker() {
        if (!hasSelection()) {
            return;
        }
        stickers.remove(selectedIndex);
        selectedIndex = stickers.isEmpty() ? -1 : stickers.size() - 1;
        invalidate();
    }

    public String getSelectedText() {
        TextSticker selected = getSelectedSticker();
        return selected == null ? "" : selected.text;
    }

    public void renderToCanvas(Canvas canvas, int outWidth, int outHeight) {
        for (TextSticker sticker : stickers) {
            drawSticker(canvas, sticker, outWidth, outHeight, false);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < stickers.size(); i++) {
            drawSticker(canvas, stickers.get(i), getWidth(), getHeight(), editingEnabled && i == selectedIndex);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editingEnabled || stickers.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedIndex = findStickerAt(event.getX(), event.getY());
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                invalidate();
                return selectedIndex >= 0;
            case MotionEvent.ACTION_MOVE:
                if (selectedIndex < 0) {
                    return false;
                }
                TextSticker selected = stickers.get(selectedIndex);
                float dx = (event.getX() - lastTouchX) / getWidth();
                float dy = (event.getY() - lastTouchY) / getHeight();
                selected.centerXFraction = clamp(selected.centerXFraction + dx);
                selected.centerYFraction = clamp(selected.centerYFraction + dy);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                return selectedIndex >= 0;
            default:
                return false;
        }
    }

    private void drawSticker(Canvas canvas, TextSticker sticker, int width, int height, boolean drawSelection) {
        if (sticker == null || sticker.text == null || width <= 0 || height <= 0) {
            return;
        }

        float minDimension = Math.min(width, height);
        float textSize = Math.max(dp(18f), sticker.sizeFraction * minDimension);
        float centerX = sticker.centerXFraction * width;
        float centerY = sticker.centerYFraction * height;

        Typeface typeface = resolveTypeface(sticker.style);
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(textSize);
        textPaint.setColor(sticker.color);

        outlinePaint.setTypeface(typeface);
        outlinePaint.setTextSize(textSize);

        String[] lines = sticker.text.split("\\n");
        float totalHeight = (lines.length - 1) * textSize * 1.1f;
        float startY = centerY - (totalHeight / 2f);

        float maxWidth = 0f;
        for (String line : lines) {
            textPaint.getTextBounds(line, 0, line.length(), measureBounds);
            maxWidth = Math.max(maxWidth, measureBounds.width());
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineY = startY + (i * textSize * 1.1f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
            if (sticker.style == StickerStyle.OUTLINE) {
                canvas.drawText(line, centerX, lineY, outlinePaint);
                canvas.drawText(line, centerX, lineY, textPaint);
            } else if (sticker.style == StickerStyle.SCRIPT) {
                textPaint.setShadowLayer(dp(6f), 0f, dp(2f), 0x33000000);
                canvas.drawText(line, centerX, lineY, textPaint);
                textPaint.clearShadowLayer();
            } else {
                textPaint.clearShadowLayer();
                canvas.drawText(line, centerX, lineY, textPaint);
            }
        }

        if (drawSelection) {
            RectF bounds = new RectF(
                    centerX - (maxWidth / 2f) - dp(12f),
                    startY - textSize * 0.8f,
                    centerX + (maxWidth / 2f) + dp(12f),
                    startY + totalHeight + textSize * 0.7f
            );
            canvas.drawRoundRect(bounds, dp(12f), dp(12f), selectionPaint);
            sticker.touchBounds.set(bounds);
        } else {
            sticker.touchBounds.set(
                    centerX - (maxWidth / 2f) - dp(12f),
                    startY - textSize * 0.8f,
                    centerX + (maxWidth / 2f) + dp(12f),
                    startY + totalHeight + textSize * 0.7f
            );
        }
    }

    private int findStickerAt(float x, float y) {
        for (int i = stickers.size() - 1; i >= 0; i--) {
            TextSticker sticker = stickers.get(i);
            if (sticker.touchBounds.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private TextSticker getSelectedSticker() {
        if (!hasSelection()) {
            return null;
        }
        return stickers.get(selectedIndex);
    }

    private Typeface resolveTypeface(StickerStyle style) {
        if (style == StickerStyle.SCRIPT) {
            return Typeface.create("serif", Typeface.BOLD_ITALIC);
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    }

    private float clamp(float value) {
        return Math.max(0.08f, Math.min(0.92f, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static class TextSticker {
        String text;
        int color;
        float sizeFraction;
        float centerXFraction;
        float centerYFraction;
        StickerStyle style = StickerStyle.BOLD;
        final RectF touchBounds = new RectF();
    }
}
