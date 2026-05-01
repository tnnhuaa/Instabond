package com.example.instabond_fe.view;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityCreateStoryBinding;
import com.example.instabond_fe.databinding.DialogImageEditorTextBinding;
import com.example.instabond_fe.model.StoryResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateStoryActivity extends AppCompatActivity {
    private enum OverlaySelection {
        NONE,
        TEXT,
        ICON
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    public static final String EXTRA_REFRESH_STORIES = "refresh_stories";

    private static final int MAX_SOURCE_EDGE = 1600;
    private static final int[] STORY_EDITOR_COLORS = {
            Color.WHITE,
            0xFFE1306C,
            0xFFF9A826,
            0xFF80D8FF,
            0xFFCB80FE
    };
    private static final int[] STORY_ICON_RES_IDS = {
            R.drawable.ic_heart_filled,
            R.drawable.ic_star,
            R.drawable.ic_music,
            R.drawable.ic_story_sparkles,
            R.drawable.ic_flame,
            R.drawable.ic_location_pin
    };

    private ActivityCreateStoryBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;

    private Uri selectedImageUri;
    private Bitmap selectedBitmap;
    private OverlaySelection selectedOverlay = OverlaySelection.NONE;
    private int textOverlayColorIndex;
    private int iconOverlayColorIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateStoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        registerLaunchers();
        setupToolbar();
        setupOverlayEditor();
        setupActions();
        loadCurrentUser();
        renderPreview();
    }

    private void setupToolbar() {
        binding.btnBackStoryComposer.setOnClickListener(v -> finish());
        binding.btnShareStory.setOnClickListener(v -> submitStory());
        binding.btnStorySettings.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_create_tools_soon, Toast.LENGTH_SHORT).show()
        );
    }

    private void setupOverlayEditor() {
        binding.tvStoryTextOverlay.setOnTouchListener(buildOverlayTouchListener(OverlaySelection.TEXT));
        binding.ivStoryIconOverlay.setOnTouchListener(buildOverlayTouchListener(OverlaySelection.ICON));
        binding.tvStoryTextOverlay.setOnClickListener(v -> selectOverlay(OverlaySelection.TEXT));
        binding.ivStoryIconOverlay.setOnClickListener(v -> selectOverlay(OverlaySelection.ICON));
        applyTextOverlayColor();
        applyIconOverlayColor();
        refreshOverlaySelectionState();
    }

    private void setupActions() {
        View.OnClickListener toolsSoonClick = v ->
                Toast.makeText(this, R.string.story_create_tools_soon, Toast.LENGTH_SHORT).show();

        binding.previewCard.setOnClickListener(v -> {
            if (hasSelectedImage()) {
                pickImageLauncher.launch("image/*");
            }
        });
        binding.btnChooseStoryPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnCaptureStoryPhoto.setOnClickListener(v -> takePhotoLauncher.launch(null));
        binding.btnReplaceStoryPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnStoryToolFlash.setOnClickListener(toolsSoonClick);
        binding.btnStoryToolText.setOnClickListener(v -> showTextOverlayDialog());
        binding.btnStoryToolComment.setOnClickListener(v -> showIconPickerDialog());
        binding.btnStoryToolEffects.setOnClickListener(v -> cycleSelectedOverlayColor());
        binding.btnStoryToolMore.setOnClickListener(v -> resetStoryDecorations());
    }

    private View.OnTouchListener buildOverlayTouchListener(OverlaySelection overlaySelection) {
        return new View.OnTouchListener() {
            private float startRawX;
            private float startRawY;
            private float startX;
            private float startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getVisibility() != View.VISIBLE) {
                    return false;
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        selectOverlay(overlaySelection);
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        startX = v.getX();
                        startY = v.getY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        dragging = true;
                        moveOverlayWithinStage(
                                v,
                                startX + (event.getRawX() - startRawX),
                                startY + (event.getRawY() - startRawY)
                        );
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void moveOverlayWithinStage(View overlay, float targetX, float targetY) {
        int stageWidth = binding.storyOverlayStage.getWidth();
        int stageHeight = binding.storyOverlayStage.getHeight();
        if (stageWidth <= 0 || stageHeight <= 0) {
            overlay.setX(targetX);
            overlay.setY(targetY);
            return;
        }

        float clampedX = Math.max(0f, Math.min(targetX, stageWidth - overlay.getWidth()));
        float clampedY = Math.max(0f, Math.min(targetY, stageHeight - overlay.getHeight()));
        overlay.setX(clampedX);
        overlay.setY(clampedY);
    }

    private void selectOverlay(OverlaySelection overlaySelection) {
        if (overlaySelection == OverlaySelection.TEXT
                && binding.tvStoryTextOverlay.getVisibility() != View.VISIBLE) {
            overlaySelection = OverlaySelection.NONE;
        } else if (overlaySelection == OverlaySelection.ICON
                && binding.ivStoryIconOverlay.getVisibility() != View.VISIBLE) {
            overlaySelection = OverlaySelection.NONE;
        }

        selectedOverlay = overlaySelection;
        refreshOverlaySelectionState();
    }

    private void refreshOverlaySelectionState() {
        binding.tvStoryTextOverlay.setBackground(
                selectedOverlay == OverlaySelection.TEXT ? buildSelectionBackground() : null
        );
        binding.ivStoryIconOverlay.setBackground(
                selectedOverlay == OverlaySelection.ICON ? buildSelectionBackground() : null
        );
    }

    private Drawable buildSelectionBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(0x1AFFFFFF);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), 0xCCFFFFFF);
        return drawable;
    }

    private void showTextOverlayDialog() {
        if (!hasSelectedImage()) {
            Toast.makeText(this, R.string.story_create_editor_pick_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean editingExisting = binding.tvStoryTextOverlay.getVisibility() == View.VISIBLE;
        DialogImageEditorTextBinding dialogBinding =
                DialogImageEditorTextBinding.inflate(getLayoutInflater());
        dialogBinding.tvDialogTitle.setText(
                editingExisting
                        ? R.string.story_create_editor_text_title_edit
                        : R.string.story_create_editor_text_title_add
        );
        dialogBinding.etTextContent.setHint(R.string.story_create_editor_text_hint);
        dialogBinding.etTextContent.setText(editingExisting ? binding.tvStoryTextOverlay.getText() : "");

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
            binding.tvStoryTextOverlay.setText(text);
            binding.tvStoryTextOverlay.setVisibility(View.VISIBLE);
            applyTextOverlayColor();
            selectOverlay(OverlaySelection.TEXT);
            if (!editingExisting) {
                centerOverlay(binding.tvStoryTextOverlay, 0.5f, 0.3f);
                Toast.makeText(this, R.string.story_create_editor_drag_hint, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.story_create_editor_text_updated, Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showIconPickerDialog() {
        if (!hasSelectedImage()) {
            Toast.makeText(this, R.string.story_create_editor_pick_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] labels = {
                getString(R.string.story_create_editor_icon_heart),
                getString(R.string.story_create_editor_icon_star),
                getString(R.string.story_create_editor_icon_music),
                getString(R.string.story_create_editor_icon_sparkle),
                getString(R.string.story_create_editor_icon_flame),
                getString(R.string.story_create_editor_icon_pin)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.story_create_editor_icon_title)
                .setItems(labels, (dialog, which) -> {
                    binding.ivStoryIconOverlay.setImageResource(STORY_ICON_RES_IDS[which]);
                    binding.ivStoryIconOverlay.setVisibility(View.VISIBLE);
                    applyIconOverlayColor();
                    selectOverlay(OverlaySelection.ICON);
                    if (binding.ivStoryIconOverlay.getX() == 0f && binding.ivStoryIconOverlay.getY() == 0f) {
                        centerOverlay(binding.ivStoryIconOverlay, 0.5f, 0.5f);
                    }
                    Toast.makeText(this, R.string.story_create_editor_drag_hint, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void cycleSelectedOverlayColor() {
        if (!hasSelectedImage()) {
            Toast.makeText(this, R.string.story_create_editor_pick_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedOverlay == OverlaySelection.TEXT
                && binding.tvStoryTextOverlay.getVisibility() == View.VISIBLE) {
            textOverlayColorIndex = (textOverlayColorIndex + 1) % STORY_EDITOR_COLORS.length;
            applyTextOverlayColor();
            Toast.makeText(this, R.string.story_create_editor_color_changed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedOverlay == OverlaySelection.ICON
                && binding.ivStoryIconOverlay.getVisibility() == View.VISIBLE) {
            iconOverlayColorIndex = (iconOverlayColorIndex + 1) % STORY_EDITOR_COLORS.length;
            applyIconOverlayColor();
            Toast.makeText(this, R.string.story_create_editor_color_changed, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.story_create_editor_color_pick_first, Toast.LENGTH_SHORT).show();
    }

    private void resetStoryDecorations() {
        boolean hasText = binding.tvStoryTextOverlay.getVisibility() == View.VISIBLE;
        boolean hasIcon = binding.ivStoryIconOverlay.getVisibility() == View.VISIBLE;
        if (!hasText && !hasIcon) {
            Toast.makeText(this, R.string.story_create_editor_remove_nothing, Toast.LENGTH_SHORT).show();
            return;
        }

        clearStoryOverlays();
        Toast.makeText(this, R.string.story_create_editor_removed, Toast.LENGTH_SHORT).show();
    }

    private void applyTextOverlayColor() {
        int color = STORY_EDITOR_COLORS[textOverlayColorIndex];
        binding.tvStoryTextOverlay.setTextColor(color);
    }

    private void applyIconOverlayColor() {
        int color = STORY_EDITOR_COLORS[iconOverlayColorIndex];
        binding.ivStoryIconOverlay.setColorFilter(color);
    }

    private void centerOverlay(View overlay, float centerXFraction, float centerYFraction) {
        overlay.post(() -> moveOverlayWithinStage(
                overlay,
                (binding.storyOverlayStage.getWidth() * centerXFraction) - (overlay.getWidth() / 2f),
                (binding.storyOverlayStage.getHeight() * centerYFraction) - (overlay.getHeight() / 2f)
        ));
    }

    private void clearStoryOverlays() {
        binding.tvStoryTextOverlay.setVisibility(View.GONE);
        binding.tvStoryTextOverlay.setText(null);
        binding.tvStoryTextOverlay.setX(0f);
        binding.tvStoryTextOverlay.setY(0f);
        binding.ivStoryIconOverlay.setVisibility(View.GONE);
        binding.ivStoryIconOverlay.setImageDrawable(null);
        binding.ivStoryIconOverlay.setX(0f);
        binding.ivStoryIconOverlay.setY(0f);
        textOverlayColorIndex = 0;
        iconOverlayColorIndex = 0;
        applyTextOverlayColor();
        applyIconOverlayColor();
        selectOverlay(OverlaySelection.NONE);
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            selectedImageUri = uri;
            try {
                selectedBitmap = decodeBitmap(uri);
                clearStoryOverlays();
                renderPreview();
            } catch (IOException e) {
                Toast.makeText(this, R.string.story_create_image_error, Toast.LENGTH_SHORT).show();
            }
        });

        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                return;
            }
            selectedBitmap = limitBitmapSize(bitmap, MAX_SOURCE_EDGE);
            selectedImageUri = saveBitmapToCacheUri(selectedBitmap);
            clearStoryOverlays();
            renderPreview();
        });
    }

    private void loadCurrentUser() {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        apiService.getUserProfile(userId).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call,
                                   @NonNull Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                UserProfileResponse profile = response.body();
                AvatarLoader.load(binding.ivStoryComposerAvatar, profile.getAvatarUrl());
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                // Keep placeholder.
            }
        });
    }

    private void renderPreview() {
        boolean hasImage = hasSelectedImage();
        binding.btnShareStory.setEnabled(hasImage);
        binding.btnShareStory.setAlpha(hasImage ? 1f : 0.5f);
        binding.btnReplaceStoryPhoto.setEnabled(hasImage);
        binding.btnReplaceStoryPhoto.setAlpha(hasImage ? 1f : 0.5f);
        binding.storyCameraControls.setVisibility(hasImage ? View.GONE : View.VISIBLE);
        binding.storyShareFooter.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.storyOverlayStage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.previewCard.setClickable(hasImage);
        binding.previewCard.setFocusable(hasImage);
        binding.storyPreviewBottomScrim.setAlpha(hasImage ? 0.9f : 0.65f);

        if (!hasImage) {
            selectOverlay(OverlaySelection.NONE);
            binding.ivStoryPreview.setImageDrawable(null);
            binding.ivStoryPreview.setBackgroundResource(R.drawable.story_create_camera_preview_bg);
            return;
        }

        if (selectedBitmap != null) {
            binding.ivStoryPreview.setImageBitmap(selectedBitmap);
        } else {
            Glide.with(this)
                    .load(selectedImageUri)
                    .centerCrop()
                    .placeholder(R.drawable.create_post_preview_placeholder)
                    .into(binding.ivStoryPreview);
        }
    }

    private boolean hasSelectedImage() {
        return selectedBitmap != null || selectedImageUri != null;
    }

    private void submitStory() {
        if (selectedImageUri == null && selectedBitmap == null) {
            Toast.makeText(this, R.string.story_create_pick_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        try {
            File uploadFile = createUploadFile();
            if (uploadFile == null) {
                setLoading(false);
                Toast.makeText(this, R.string.story_create_image_error, Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), uploadFile);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", uploadFile.getName(), fileBody);

            apiService.createStory(filePart).enqueue(new Callback<StoryResponse>() {
                @Override
                public void onResponse(@NonNull Call<StoryResponse> call,
                                       @NonNull Response<StoryResponse> response) {
                    setLoading(false);
                    if (!response.isSuccessful()) {
                        if (sessionManager.isLoggedIn()) {
                            Toast.makeText(CreateStoryActivity.this, R.string.story_create_failed, Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    Toast.makeText(CreateStoryActivity.this, R.string.story_create_success, Toast.LENGTH_SHORT).show();
                    openFeedWithRefresh();
                }

                @Override
                public void onFailure(@NonNull Call<StoryResponse> call, @NonNull Throwable t) {
                    setLoading(false);
                    if (sessionManager.isLoggedIn()) {
                        Toast.makeText(
                                CreateStoryActivity.this,
                                getString(R.string.msg_connection_error, t.getMessage()),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            });
        } catch (IOException e) {
            setLoading(false);
            Toast.makeText(this, R.string.story_create_image_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File createUploadFile() throws IOException {
        Bitmap storyBitmap = buildStoryBitmapForUpload();
        if (storyBitmap == null) {
            return selectedImageUri != null ? createTempFileFromUri(selectedImageUri) : null;
        }

        File tempFile = new File(getCacheDir(), "story_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            storyBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            out.flush();
        }
        return tempFile;
    }

    private Bitmap buildStoryBitmapForUpload() throws IOException {
        Bitmap baseBitmap = selectedBitmap;
        if (baseBitmap == null && selectedImageUri != null) {
            baseBitmap = decodeBitmap(selectedImageUri);
            selectedBitmap = baseBitmap;
        }
        if (baseBitmap == null) {
            return null;
        }

        Bitmap outputBitmap = Bitmap.createBitmap(
                baseBitmap.getWidth(),
                baseBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(outputBitmap);
        canvas.drawBitmap(baseBitmap, 0f, 0f, null);

        if (binding.tvStoryTextOverlay.getVisibility() == View.VISIBLE) {
            renderOverlayViewToCanvas(canvas, binding.tvStoryTextOverlay, baseBitmap);
        }
        if (binding.ivStoryIconOverlay.getVisibility() == View.VISIBLE) {
            renderOverlayViewToCanvas(canvas, binding.ivStoryIconOverlay, baseBitmap);
        }

        return outputBitmap;
    }

    private void renderOverlayViewToCanvas(Canvas canvas, View overlayView, Bitmap baseBitmap) {
        if (overlayView.getWidth() <= 0 || overlayView.getHeight() <= 0 || baseBitmap == null) {
            return;
        }

        int previewWidth = binding.ivStoryPreview.getWidth();
        int previewHeight = binding.ivStoryPreview.getHeight();
        if (previewWidth <= 0 || previewHeight <= 0) {
            return;
        }

        float scale = Math.max(
                previewWidth / (float) baseBitmap.getWidth(),
                previewHeight / (float) baseBitmap.getHeight()
        );
        float offsetX = (previewWidth - (baseBitmap.getWidth() * scale)) / 2f;
        float offsetY = (previewHeight - (baseBitmap.getHeight() * scale)) / 2f;
        Drawable originalBackground = overlayView.getBackground();

        overlayView.setBackground(null);
        canvas.save();
        canvas.translate((overlayView.getX() - offsetX) / scale, (overlayView.getY() - offsetY) / scale);
        canvas.scale(1f / scale, 1f / scale);
        overlayView.draw(canvas);
        canvas.restore();
        overlayView.setBackground(originalBackground);
    }

    private File createTempFileFromUri(Uri uri) throws IOException {
        String fileName = queryDisplayName(uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "story_" + System.currentTimeMillis() + ".jpg";
        }

        File tempFile = new File(getCacheDir(), fileName);
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile, false)) {
            if (inputStream == null) {
                return null;
            }
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        }
        return tempFile;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        }
        return null;
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
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) {
            Bitmap.Config config = bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
            return bitmap.copy(config, false);
        }

        float scale = maxEdge / (float) longest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private Uri saveBitmapToCacheUri(Bitmap bitmap) {
        File file = new File(getCacheDir(), "story_capture_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            out.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Toast.makeText(this, R.string.story_create_image_error, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void setLoading(boolean loading) {
        boolean hasImage = hasSelectedImage();
        binding.btnShareStory.setEnabled(!loading && hasImage);
        binding.btnBackStoryComposer.setEnabled(!loading);
        binding.btnStorySettings.setEnabled(!loading);
        binding.btnChooseStoryPhoto.setEnabled(!loading);
        binding.btnCaptureStoryPhoto.setEnabled(!loading);
        binding.btnReplaceStoryPhoto.setEnabled(!loading && hasImage);
        binding.previewCard.setEnabled(!loading);
        binding.btnStoryToolFlash.setEnabled(!loading);
        binding.btnStoryToolText.setEnabled(!loading);
        binding.btnStoryToolComment.setEnabled(!loading);
        binding.btnStoryToolEffects.setEnabled(!loading);
        binding.btnStoryToolMore.setEnabled(!loading);
        binding.tvStoryTextOverlay.setEnabled(!loading);
        binding.ivStoryIconOverlay.setEnabled(!loading);
        binding.btnShareStory.setText(loading
                ? getString(R.string.story_create_posting)
                : getString(R.string.story_create_post_action));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void openFeedWithRefresh() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.putExtra(EXTRA_REFRESH_STORIES, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
