package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.canhub.cropper.CropImageView;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityImageEditorBinding;
import com.example.instabond_fe.databinding.DialogImageEditorTextBinding;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.view.editor.TextStickerOverlayView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageEditorActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    public static final String EXTRA_INPUT_URI = "extra_input_uri";
    public static final String EXTRA_OUTPUT_URI = "extra_output_uri";

    private static final int MAX_SOURCE_EDGE = 1600;
    private static final int[] EDITOR_COLORS = {
            Color.WHITE,
            0xFFE1306C,
            0xFF8037B1,
            0xFFCB80FE,
            0xFFF9D77B,
            0xFFFFA08A,
            0xFFB31B25
    };

    private enum ToolMode {
        CROP,
        RESIZE,
        ADJUST,
        DRAW,
        TEXT
    }

    private enum ResizePreset {
        ORIGINAL(0),
        MEDIUM(1080),
        LARGE(1440);

        final int longestEdge;

        ResizePreset(int longestEdge) {
            this.longestEdge = longestEdge;
        }
    }

    private ActivityImageEditorBinding binding;

    private Uri inputUri;
    private Bitmap sourceBitmap;
    private Bitmap previewBitmap;

    private ToolMode toolMode = ToolMode.CROP;
    private ResizePreset resizePreset = ResizePreset.ORIGINAL;
    private int brightnessValue;
    private float contrastValue = 1f;

    private int drawColor = EDITOR_COLORS[2];
    private int textColor = EDITOR_COLORS[2];
    private float brushWidthPx;
    private float textSizeFraction = 0.14f;
    private TextStickerOverlayView.StickerStyle textStyle = TextStickerOverlayView.StickerStyle.BOLD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String rawInputUri = getIntent().getStringExtra(EXTRA_INPUT_URI);
        if (rawInputUri == null || rawInputUri.trim().isEmpty()) {
            Toast.makeText(this, R.string.create_post_select_photo_first, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        inputUri = Uri.parse(rawInputUri);
        try {
            sourceBitmap = decodeBitmap(inputUri);
        } catch (IOException e) {
            Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        brushWidthPx = dp(10f);
        setupToolbar();
        setupEditorState();
        setupToolControls();
        updatePreviewBitmap();

        binding.cropImageView.setImageBitmap(sourceBitmap);
        switchMode(ToolMode.CROP);
    }

    private void setupToolbar() {
        binding.btnCloseEditor.setOnClickListener(v -> finish());
        binding.btnDoneEditor.setOnClickListener(v -> deliverEditedImage());
    }

    private void setupEditorState() {
        binding.chipEditorResizeOriginal.setChecked(true);
        binding.seekBrightness.setProgress(60);
        binding.seekContrast.setProgress(50);
        binding.seekBrushSize.setProgress(8);
        binding.seekTextSize.setProgress(22);
        binding.chipTextStyleBold.setChecked(true);

        binding.viewDrawingOverlay.setBrushColor(drawColor);
        binding.viewDrawingOverlay.setBrushWidthPx(brushWidthPx);
        binding.viewTextOverlay.setEditingEnabled(false);

        populateColorPalette(binding.layoutDrawColors, true);
        populateColorPalette(binding.layoutTextColors, false);
    }

    private void setupToolControls() {
        binding.tabCrop.setOnClickListener(v -> switchMode(ToolMode.CROP));
        binding.tabResize.setOnClickListener(v -> switchMode(ToolMode.RESIZE));
        binding.tabAdjust.setOnClickListener(v -> switchMode(ToolMode.ADJUST));
        binding.tabDraw.setOnClickListener(v -> switchMode(ToolMode.DRAW));
        binding.tabText.setOnClickListener(v -> switchMode(ToolMode.TEXT));

        binding.btnRotateLeftEditor.setOnClickListener(v -> binding.cropImageView.rotateImage(-90));
        binding.btnRotateRightEditor.setOnClickListener(v -> binding.cropImageView.rotateImage(90));

        binding.chipGroupEditorResize.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = group.getCheckedChipId();
            if (checkedId == binding.chipEditorResizeMedium.getId()) {
                resizePreset = ResizePreset.MEDIUM;
            } else if (checkedId == binding.chipEditorResizeLarge.getId()) {
                resizePreset = ResizePreset.LARGE;
            } else {
                resizePreset = ResizePreset.ORIGINAL;
            }
            updatePreviewBitmap();
        });

        binding.seekBrightness.setOnSeekBarChangeListener(onSeekChange((seekBar, progress, fromUser) -> {
            brightnessValue = progress - 60;
            updatePreviewBitmap();
        }));

        binding.seekContrast.setOnSeekBarChangeListener(onSeekChange((seekBar, progress, fromUser) -> {
            contrastValue = 0.5f + (progress / 50f);
            updatePreviewBitmap();
        }));

        binding.seekBrushSize.setOnSeekBarChangeListener(onSeekChange((seekBar, progress, fromUser) -> {
            brushWidthPx = dp(4f + progress);
            binding.viewDrawingOverlay.setBrushWidthPx(brushWidthPx);
        }));

        binding.seekTextSize.setOnSeekBarChangeListener(onSeekChange((seekBar, progress, fromUser) -> {
            textSizeFraction = 0.08f + (progress / 260f);
            binding.viewTextOverlay.updateSelectedSizeFraction(textSizeFraction);
        }));

        binding.chipGroupTextStyle.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = group.getCheckedChipId();
            if (checkedId == binding.chipTextStyleScript.getId()) {
                textStyle = TextStickerOverlayView.StickerStyle.SCRIPT;
            } else if (checkedId == binding.chipTextStyleOutline.getId()) {
                textStyle = TextStickerOverlayView.StickerStyle.OUTLINE;
            } else {
                textStyle = TextStickerOverlayView.StickerStyle.BOLD;
            }
            binding.viewTextOverlay.updateSelectedStyle(textStyle);
        });

        binding.btnClearDrawing.setOnClickListener(v -> binding.viewDrawingOverlay.clear());
        binding.btnAddTextSticker.setOnClickListener(v -> showTextStickerDialog(false));
        binding.btnEditTextSticker.setOnClickListener(v -> showTextStickerDialog(true));
        binding.btnRemoveTextSticker.setOnClickListener(v -> binding.viewTextOverlay.removeSelectedSticker());
    }

    private void populateColorPalette(LinearLayout container, boolean drawingPalette) {
        container.removeAllViews();
        for (int i = 0; i < EDITOR_COLORS.length; i++) {
            int color = EDITOR_COLORS[i];
            ImageView swatch = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
            if (i > 0) {
                params.leftMargin = dp(8);
            }
            swatch.setLayoutParams(params);
            swatch.setBackground(buildColorBackground(color, drawingPalette ? color == drawColor : color == textColor));
            swatch.setOnClickListener(v -> {
                if (drawingPalette) {
                    drawColor = color;
                    binding.viewDrawingOverlay.setBrushColor(color);
                    populateColorPalette(binding.layoutDrawColors, true);
                } else {
                    textColor = color;
                    binding.viewTextOverlay.updateSelectedColor(color);
                    populateColorPalette(binding.layoutTextColors, false);
                }
            });
            container.addView(swatch);
        }
    }

    private GradientDrawable buildColorBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (selected) {
            drawable.setStroke(dp(3), ContextCompat.getColor(this, R.color.login_bg_start));
        } else {
            drawable.setStroke(dp(1), 0x33AFACAC);
        }
        return drawable;
    }

    private void switchMode(ToolMode mode) {
        if (toolMode == ToolMode.CROP && mode != ToolMode.CROP) {
            Bitmap cropped = binding.cropImageView.getCroppedImage();
            if (cropped != null) {
                sourceBitmap = cropped;
                updatePreviewBitmap();
            }
        }

        toolMode = mode;
        binding.layoutCropTools.setVisibility(mode == ToolMode.CROP ? View.VISIBLE : View.GONE);
        binding.layoutResizeTools.setVisibility(mode == ToolMode.RESIZE ? View.VISIBLE : View.GONE);
        binding.layoutAdjustTools.setVisibility(mode == ToolMode.ADJUST ? View.VISIBLE : View.GONE);
        binding.layoutDrawTools.setVisibility(mode == ToolMode.DRAW ? View.VISIBLE : View.GONE);
        binding.layoutTextTools.setVisibility(mode == ToolMode.TEXT ? View.VISIBLE : View.GONE);

        if (mode == ToolMode.CROP) {
            binding.cropImageView.setVisibility(View.VISIBLE);
            binding.ivEditorPreview.setVisibility(View.GONE);
            binding.viewDrawingOverlay.setVisibility(View.GONE);
            binding.viewTextOverlay.setVisibility(View.GONE);
        } else {
            binding.cropImageView.setVisibility(View.GONE);
            binding.ivEditorPreview.setVisibility(View.VISIBLE);
            binding.viewDrawingOverlay.setVisibility(View.VISIBLE);
            binding.viewTextOverlay.setVisibility(View.VISIBLE);
        }

        binding.viewDrawingOverlay.setDrawingEnabled(mode == ToolMode.DRAW);
        binding.viewTextOverlay.setEditingEnabled(mode == ToolMode.TEXT);

        styleTab(binding.tabCrop, mode == ToolMode.CROP, R.drawable.ic_editor_crop);
        styleTab(binding.tabResize, mode == ToolMode.RESIZE, R.drawable.ic_editor_resize);
        styleTab(binding.tabAdjust, mode == ToolMode.ADJUST, R.drawable.ic_editor_adjust);
        styleTab(binding.tabDraw, mode == ToolMode.DRAW, R.drawable.ic_editor_draw);
        styleTab(binding.tabText, mode == ToolMode.TEXT, R.drawable.ic_editor_text);
    }

    private void styleTab(LinearLayout tab, boolean active, int iconRes) {
        tab.setBackgroundResource(active ? R.drawable.bg_image_editor_active_tab : 0);
        ImageView icon = (ImageView) tab.getChildAt(0);
        TextView label = (TextView) tab.getChildAt(1);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ContextCompat.getColor(this, active ? android.R.color.white : R.color.login_text_secondary));
        label.setTextColor(ContextCompat.getColor(this, active ? android.R.color.white : R.color.login_text_secondary));
    }

    private void updatePreviewBitmap() {
        previewBitmap = buildBaseBitmap();
        binding.ivEditorPreview.setImageBitmap(previewBitmap);
    }

    private Bitmap buildBaseBitmap() {
        Bitmap adjusted = applyBrightnessContrast(sourceBitmap, brightnessValue, contrastValue);
        return resizeBitmap(adjusted, resizePreset);
    }

    private Bitmap buildFinalBitmap() {
        Bitmap base = buildBaseBitmap();
        Bitmap result = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(base, 0f, 0f, null);
        binding.viewDrawingOverlay.renderToCanvas(canvas, result.getWidth(), result.getHeight());
        binding.viewTextOverlay.renderToCanvas(canvas, result.getWidth(), result.getHeight());
        return result;
    }

    private Bitmap applyBrightnessContrast(Bitmap source, int brightness, float contrast) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float translate = (-0.5f * contrast + 0.5f) * 255f + brightness;
        ColorMatrix matrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0f, 0f, paint);
        return result;
    }

    private Bitmap resizeBitmap(Bitmap source, ResizePreset preset) {
        if (preset == ResizePreset.ORIGINAL) return copyBitmap(source);

        int width = source.getWidth();
        int height = source.getHeight();
        int longestEdge = Math.max(width, height);
        if (longestEdge <= preset.longestEdge) return copyBitmap(source);

        float scale = preset.longestEdge / (float) longestEdge;
        return Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private Bitmap copyBitmap(Bitmap source) {
        Bitmap.Config config = source.getConfig() != null ? source.getConfig() : Bitmap.Config.ARGB_8888;
        return source.copy(config, false);
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        ImageDecoder.Source decoderSource = ImageDecoder.createSource(getContentResolver(), uri);
        Bitmap bitmap = ImageDecoder.decodeBitmap(decoderSource, (decoder, info, source) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int longest = Math.max(width, height);
            if (longest > MAX_SOURCE_EDGE) {
                float scale = MAX_SOURCE_EDGE / (float) longest;
                decoder.setTargetSize(
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale))
                );
            }
        });
        return limitBitmapSize(bitmap, MAX_SOURCE_EDGE);
    }

    private Bitmap limitBitmapSize(Bitmap bitmap, int maxEdge) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) return copyBitmap(bitmap);

        float scale = maxEdge / (float) longest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private void showTextStickerDialog(boolean editExisting) {
        if (editExisting && !binding.viewTextOverlay.hasSelection()) {
            Toast.makeText(this, R.string.image_editor_select_text_first, Toast.LENGTH_SHORT).show();
            return;
        }

        DialogImageEditorTextBinding dialogBinding =
                DialogImageEditorTextBinding.inflate(getLayoutInflater());
        dialogBinding.tvDialogTitle.setText(
                editExisting ? R.string.image_editor_edit_text : R.string.image_editor_add_text);
        dialogBinding.etTextContent.setText(
                editExisting ? binding.viewTextOverlay.getSelectedText() : "");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogBinding.btnCancelTextDialog.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnApplyTextDialog.setOnClickListener(v -> {
            CharSequence value = dialogBinding.etTextContent.getText();
            String text = value == null ? "" : value.toString().trim();
            if (text.isEmpty()) {
                dialogBinding.inputLayoutText.setError(getString(R.string.image_editor_text_required));
                return;
            }

            dialogBinding.inputLayoutText.setError(null);
            if (editExisting) {
                binding.viewTextOverlay.updateSelectedText(text);
            } else {
                binding.viewTextOverlay.addSticker(text, textColor, textSizeFraction, textStyle);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void deliverEditedImage() {
        if (toolMode == ToolMode.CROP) {
            Bitmap cropped = binding.cropImageView.getCroppedImage();
            if (cropped != null) {
                sourceBitmap = cropped;
            }
        }

        try {
            Bitmap output = buildFinalBitmap();
            File file = new File(getCacheDir(), "edited_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                output.compress(Bitmap.CompressFormat.JPEG, 94, out);
            }

            Intent result = new Intent();
            result.putExtra(EXTRA_OUTPUT_URI, Uri.fromFile(file).toString());
            setResult(RESULT_OK, result);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, R.string.create_post_camera_save_error, Toast.LENGTH_SHORT).show();
        }
    }

    private SeekBar.OnSeekBarChangeListener onSeekChange(SeekBarValueListener listener) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                listener.onValue(seekBar, progress, fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }

    private interface SeekBarValueListener {
        void onValue(@NonNull SeekBar seekBar, int progress, boolean fromUser);
    }
}