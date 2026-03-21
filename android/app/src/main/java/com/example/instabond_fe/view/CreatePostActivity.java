package com.example.instabond_fe.view;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityCreatePostBinding;
import com.example.instabond_fe.databinding.DialogEditPhotoBinding;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreatePostActivity extends AppCompatActivity {
    private static final String EXTRA_REFRESH_FEED = "refresh_feed";

    public enum FilterType {
        NORMAL,
        VIVID,
        BW,
        VINTAGE,
        PULSE
    }

    private enum CropRatio {
        ORIGINAL(0f),
        SQUARE(1f),
        PORTRAIT(4f / 5f),
        LANDSCAPE(16f / 9f);

        private final float ratio;

        CropRatio(float ratio) {
            this.ratio = ratio;
        }
    }

    private enum ResizePreset {
        ORIGINAL(0),
        MEDIUM(1080),
        LARGE(1440);

        private final int longestEdge;

        ResizePreset(int longestEdge) {
            this.longestEdge = longestEdge;
        }
    }

    private interface ValueConsumer {
        void accept(String value);
    }

    private static final int MAX_SOURCE_EDGE = 1600;
    private static final int FILTER_THUMBNAIL_SIZE = 160;

    private ActivityCreatePostBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private PhotoFilterAdapter filterAdapter;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;

    private Uri selectedImageUri;
    private Bitmap sourceBitmap;
    private Bitmap renderedBitmap;

    private FilterType activeFilter = FilterType.NORMAL;
    private CropRatio cropRatio = CropRatio.ORIGINAL;
    private ResizePreset resizePreset = ResizePreset.ORIGINAL;
    private int rotationDegrees = 0;

    private String tagsText = "";
    private String locationText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        registerLaunchers();
        setupToolbar();
        setupBottomNav();
        setupFilterStrip();
        setupActions();
        styleSwitch(binding.switchFacebook);
        styleSwitch(binding.switchTwitter);
        updateOptionSummaries();
        renderEditorState();
        loadCurrentUser();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnPost.setOnClickListener(v -> submitPost());
    }

    private void setupBottomNav() {
        View navHome = binding.bottomNav.findViewById(R.id.nav_home);
        View navSearch = binding.bottomNav.findViewById(R.id.nav_search);
        View navNotifications = binding.bottomNav.findViewById(R.id.nav_notifications);
        View navProfile = binding.bottomNav.findViewById(R.id.nav_profile);
        View btnCreate = binding.bottomNav.findViewById(R.id.btn_create);

        clearBottomNavHighlight(navHome, navSearch, navNotifications, navProfile);
        navHome.setOnClickListener(v -> navigateTo(NewsfeedActivity.class));
        navSearch.setOnClickListener(v -> navigateTo(SearchActivity.class));
        navNotifications.setOnClickListener(v -> navigateTo(NotificationsActivity.class));
        navProfile.setOnClickListener(v -> navigateTo(ProfileActivity.class));
        btnCreate.setOnClickListener(v -> {
            // Current screen.
        });
    }

    private void clearBottomNavHighlight(View... views) {
        for (View view : views) {
            if (view instanceof FrameLayout) {
                view.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private void navigateTo(Class<?> destination) {
        if (getClass().equals(destination)) {
            return;
        }
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupFilterStrip() {
        filterAdapter = new PhotoFilterAdapter(this::onFilterSelected);
        binding.rvFilters.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvFilters.setAdapter(filterAdapter);
        updateFilterStrip();
    }

    private void setupActions() {
        binding.cardPreview.setOnClickListener(v -> {
            if (sourceBitmap == null) {
                showImageSourceDialog();
            } else {
                showEditToolsDialog();
            }
        });
        binding.btnEditImage.setOnClickListener(v -> showEditToolsDialog());
        binding.cardLocation.setOnClickListener(v -> showTextInputDialog(
                getString(R.string.create_post_dialog_location),
                getString(R.string.create_post_dialog_hint_location),
                locationText,
                value -> {
                    locationText = value;
                    updateOptionSummaries();
                }));
        binding.cardTagPeople.setOnClickListener(v -> showTextInputDialog(
                getString(R.string.create_post_dialog_tag),
                getString(R.string.create_post_dialog_hint_tag),
                tagsText,
                value -> {
                    tagsText = value;
                    updateOptionSummaries();
                }));
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            selectedImageUri = uri;
            try {
                sourceBitmap = decodeBitmap(uri);
                resetEdits(false);
                renderEditorState();
            } catch (IOException e) {
                Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            }
        });

        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                return;
            }
            selectedImageUri = saveBitmapToCacheUri(bitmap);
            sourceBitmap = limitBitmapSize(bitmap, MAX_SOURCE_EDGE);
            resetEdits(false);
            renderEditorState();
        });
    }

    private void loadCurrentUser() {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            binding.ivUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
            return;
        }

        apiService.getUserProfile(userId).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call,
                                   @NonNull Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    binding.ivUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
                    return;
                }

                String avatarUrl = response.body().getAvatarUrl();
                if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                    binding.ivUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
                    return;
                }

                Glide.with(CreatePostActivity.this)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.profile_placeholder_bg)
                        .error(R.drawable.profile_placeholder_bg)
                        .into(binding.ivUserAvatar);
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                binding.ivUserAvatar.setImageResource(R.drawable.profile_placeholder_bg);
            }
        });
    }

    private void showImageSourceDialog() {
        String[] options = new String[]{
                getString(R.string.create_post_choose_gallery),
                getString(R.string.create_post_take_photo)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_post_dialog_photo)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickImageLauncher.launch("image/*");
                    } else {
                        takePhotoLauncher.launch(null);
                    }
                })
                .setNegativeButton(R.string.create_post_cancel, null)
                .show();
    }

    private void showEditToolsDialog() {
        if (sourceBitmap == null) {
            showImageSourceDialog();
            return;
        }

        String[] options = new String[]{
                getString(R.string.create_post_choose_gallery),
                getString(R.string.create_post_take_photo),
                getString(R.string.create_post_crop_resize),
                getString(R.string.create_post_reset_edits)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_post_dialog_photo)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickImageLauncher.launch("image/*");
                    } else if (which == 1) {
                        takePhotoLauncher.launch(null);
                    } else if (which == 2) {
                        showEditPhotoDialog();
                    } else {
                        resetEdits(true);
                    }
                })
                .setNegativeButton(R.string.create_post_cancel, null)
                .show();
    }

    private void showEditPhotoDialog() {
        if (sourceBitmap == null) {
            Toast.makeText(this, R.string.create_post_select_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        DialogEditPhotoBinding dialogBinding = DialogEditPhotoBinding.inflate(getLayoutInflater());
        setCheckedCropChip(dialogBinding);
        setCheckedResizeChip(dialogBinding);

        final int[] previewRotation = {rotationDegrees};
        dialogBinding.tvRotationValue.setText(getString(
                R.string.create_post_rotation_value, normalizedRotation(previewRotation[0])));
        dialogBinding.btnRotateLeft.setOnClickListener(v -> {
            previewRotation[0] = normalizedRotation(previewRotation[0] - 90);
            dialogBinding.tvRotationValue.setText(getString(
                    R.string.create_post_rotation_value, normalizedRotation(previewRotation[0])));
        });
        dialogBinding.btnRotateRight.setOnClickListener(v -> {
            previewRotation[0] = normalizedRotation(previewRotation[0] + 90);
            dialogBinding.tvRotationValue.setText(getString(
                    R.string.create_post_rotation_value, normalizedRotation(previewRotation[0])));
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_post_crop_resize)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.create_post_cancel, null)
                .setPositiveButton(R.string.create_post_apply, (dialog, which) -> {
                    cropRatio = resolveCropRatio(dialogBinding);
                    resizePreset = resolveResizePreset(dialogBinding);
                    rotationDegrees = normalizedRotation(previewRotation[0]);
                    renderEditorState();
                })
                .show();
    }

    private void setCheckedCropChip(DialogEditPhotoBinding dialogBinding) {
        if (cropRatio == CropRatio.SQUARE) {
            dialogBinding.chipCropSquare.setChecked(true);
        } else if (cropRatio == CropRatio.PORTRAIT) {
            dialogBinding.chipCropPortrait.setChecked(true);
        } else if (cropRatio == CropRatio.LANDSCAPE) {
            dialogBinding.chipCropLandscape.setChecked(true);
        } else {
            dialogBinding.chipCropOriginal.setChecked(true);
        }
    }

    private void setCheckedResizeChip(DialogEditPhotoBinding dialogBinding) {
        if (resizePreset == ResizePreset.MEDIUM) {
            dialogBinding.chipResizeMedium.setChecked(true);
        } else if (resizePreset == ResizePreset.LARGE) {
            dialogBinding.chipResizeLarge.setChecked(true);
        } else {
            dialogBinding.chipResizeOriginal.setChecked(true);
        }
    }

    private CropRatio resolveCropRatio(DialogEditPhotoBinding dialogBinding) {
        int checkedId = dialogBinding.chipGroupCrop.getCheckedChipId();
        if (checkedId == dialogBinding.chipCropSquare.getId()) {
            return CropRatio.SQUARE;
        }
        if (checkedId == dialogBinding.chipCropPortrait.getId()) {
            return CropRatio.PORTRAIT;
        }
        if (checkedId == dialogBinding.chipCropLandscape.getId()) {
            return CropRatio.LANDSCAPE;
        }
        return CropRatio.ORIGINAL;
    }

    private ResizePreset resolveResizePreset(DialogEditPhotoBinding dialogBinding) {
        int checkedId = dialogBinding.chipGroupResize.getCheckedChipId();
        if (checkedId == dialogBinding.chipResizeMedium.getId()) {
            return ResizePreset.MEDIUM;
        }
        if (checkedId == dialogBinding.chipResizeLarge.getId()) {
            return ResizePreset.LARGE;
        }
        return ResizePreset.ORIGINAL;
    }

    private void showTextInputDialog(String title, String hint, String initialValue, ValueConsumer consumer) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(initialValue);

        int horizontal = dp(4);
        int vertical = dp(8);
        inputLayout.setPadding(horizontal, vertical, horizontal, 0);
        inputLayout.addView(input, new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT,
                TextInputLayout.LayoutParams.WRAP_CONTENT
        ));

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(inputLayout)
                .setNegativeButton(R.string.create_post_cancel, null)
                .setPositiveButton(R.string.create_post_apply, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    consumer.accept(value);
                })
                .show();
    }

    private void updateOptionSummaries() {
        binding.tvLocationValue.setText(locationText);
        binding.tvLocationValue.setVisibility(locationText.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvTagsValue.setText(tagsText);
        binding.tvTagsValue.setVisibility(tagsText.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void onFilterSelected(FilterType filterType) {
        if (sourceBitmap == null) {
            showImageSourceDialog();
            return;
        }
        activeFilter = filterType;
        renderEditorState();
    }

    private void renderEditorState() {
        if (sourceBitmap == null) {
            renderedBitmap = null;
            binding.ivPreview.setVisibility(View.GONE);
            binding.previewPlaceholder.setVisibility(View.VISIBLE);
            binding.tvImageHint.setVisibility(View.VISIBLE);
            updateFilterStrip();
            return;
        }

        renderedBitmap = buildOutputBitmap(sourceBitmap, activeFilter, cropRatio, resizePreset, rotationDegrees);
        binding.ivPreview.setImageBitmap(renderedBitmap);
        binding.ivPreview.setVisibility(View.VISIBLE);
        binding.previewPlaceholder.setVisibility(View.GONE);
        binding.tvImageHint.setVisibility(View.GONE);
        updateFilterStrip();
    }

    private void updateFilterStrip() {
        List<PhotoFilterAdapter.FilterPreviewItem> items = new ArrayList<>();
        items.add(new PhotoFilterAdapter.FilterPreviewItem(
                FilterType.NORMAL, getString(R.string.create_post_filter_normal), createFilterThumbnail(FilterType.NORMAL)));
        items.add(new PhotoFilterAdapter.FilterPreviewItem(
                FilterType.VIVID, getString(R.string.create_post_filter_vivid), createFilterThumbnail(FilterType.VIVID)));
        items.add(new PhotoFilterAdapter.FilterPreviewItem(
                FilterType.BW, getString(R.string.create_post_filter_bw), createFilterThumbnail(FilterType.BW)));
        items.add(new PhotoFilterAdapter.FilterPreviewItem(
                FilterType.VINTAGE, getString(R.string.create_post_filter_vintage), createFilterThumbnail(FilterType.VINTAGE)));
        items.add(new PhotoFilterAdapter.FilterPreviewItem(
                FilterType.PULSE, getString(R.string.create_post_filter_pulse), createFilterThumbnail(FilterType.PULSE)));
        filterAdapter.submitItems(items, activeFilter);
    }

    private Bitmap createFilterThumbnail(FilterType filterType) {
        if (sourceBitmap == null) {
            return null;
        }
        Bitmap previewSource = rotateBitmap(sourceBitmap, rotationDegrees);
        Bitmap square = cropCenterSquare(previewSource);
        Bitmap scaled = Bitmap.createScaledBitmap(square, FILTER_THUMBNAIL_SIZE, FILTER_THUMBNAIL_SIZE, true);
        return applyFilterToBitmap(scaled, filterType);
    }

    private Bitmap buildOutputBitmap(Bitmap original,
                                     FilterType filterType,
                                     CropRatio selectedCropRatio,
                                     ResizePreset selectedResizePreset,
                                     int rotation) {
        Bitmap rotated = rotateBitmap(original, rotation);
        Bitmap cropped = cropBitmapToRatio(rotated, selectedCropRatio);
        Bitmap filtered = applyFilterToBitmap(cropped, filterType);
        return resizeBitmap(filtered, selectedResizePreset);
    }

    private Bitmap applyFilterToBitmap(Bitmap source, FilterType filterType) {
        if (source == null) {
            return null;
        }
        switch (filterType) {
            case VIVID:
                return applyColorMatrix(source, buildVividMatrix());
            case BW:
                return applyColorMatrix(source, buildBwMatrix());
            case VINTAGE:
                return applyColorMatrix(source, buildVintageMatrix());
            case PULSE:
                return applyPulseFilter(source);
            case NORMAL:
            default:
                return copyBitmap(source);
        }
    }

    private Bitmap applyColorMatrix(Bitmap source, ColorMatrix matrix) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0f, 0f, paint);
        return result;
    }

    private ColorMatrix buildVividMatrix() {
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(1.3f);

        ColorMatrix brightnessContrast = new ColorMatrix(new float[]{
                1.08f, 0, 0, 0, 8,
                0, 1.08f, 0, 0, 8,
                0, 0, 1.08f, 0, 8,
                0, 0, 0, 1, 0
        });
        saturation.postConcat(brightnessContrast);
        return saturation;
    }

    private ColorMatrix buildBwMatrix() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        return matrix;
    }

    private ColorMatrix buildVintageMatrix() {
        return new ColorMatrix(new float[]{
                0.393f, 0.769f, 0.189f, 0, 0,
                0.349f, 0.686f, 0.168f, 0, 0,
                0.272f, 0.534f, 0.131f, 0, 0,
                0f, 0f, 0f, 1f, 0f
        });
    }

    private Bitmap applyPulseFilter(Bitmap source) {
        Bitmap result = applyColorMatrix(source, buildVividMatrix());
        Canvas canvas = new Canvas(result);
        Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.argb(36, 128, 55, 177));
        canvas.drawRect(0f, 0f, result.getWidth(), result.getHeight(), overlayPaint);
        return result;
    }

    private Bitmap cropBitmapToRatio(Bitmap source, CropRatio ratio) {
        if (source == null || ratio == CropRatio.ORIGINAL) {
            return copyBitmap(source);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        float targetRatio = ratio.ratio;
        int cropWidth = width;
        int cropHeight = height;

        if ((float) width / height > targetRatio) {
            cropWidth = Math.round(height * targetRatio);
        } else {
            cropHeight = Math.round(width / targetRatio);
        }

        int left = Math.max(0, (width - cropWidth) / 2);
        int top = Math.max(0, (height - cropHeight) / 2);
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight);
    }

    private Bitmap resizeBitmap(Bitmap source, ResizePreset preset) {
        if (source == null || preset == ResizePreset.ORIGINAL) {
            return copyBitmap(source);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int longestEdge = Math.max(width, height);
        if (longestEdge <= preset.longestEdge) {
            return copyBitmap(source);
        }

        float scale = preset.longestEdge / (float) longestEdge;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private Bitmap rotateBitmap(Bitmap source, int rotation) {
        if (source == null) {
            return null;
        }
        int normalized = normalizedRotation(rotation);
        if (normalized == 0) {
            return copyBitmap(source);
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(normalized);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap cropCenterSquare(Bitmap source) {
        if (source == null) {
            return null;
        }
        int size = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        return Bitmap.createBitmap(source, left, top, size, size);
    }

    private Bitmap copyBitmap(Bitmap source) {
        if (source == null) {
            return null;
        }
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
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) {
            return copyBitmap(bitmap);
        }

        float scale = maxEdge / (float) longest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private void resetEdits(boolean rerender) {
        activeFilter = FilterType.NORMAL;
        cropRatio = CropRatio.ORIGINAL;
        resizePreset = ResizePreset.ORIGINAL;
        rotationDegrees = 0;
        if (rerender) {
            renderEditorState();
        }
    }

    private int normalizedRotation(int rotation) {
        int normalized = rotation % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private Uri saveBitmapToCacheUri(Bitmap bitmap) {
        File file = new File(getCacheDir(), "captured_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            out.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Toast.makeText(this, R.string.create_post_camera_save_error, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void submitPost() {
        String caption = textOf(binding.etCaption);
        if (caption.isEmpty() && renderedBitmap == null && selectedImageUri == null) {
            Toast.makeText(this, R.string.create_post_validation_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        RequestBody requestPart = RequestBody.create(
                MediaType.parse("application/json"),
                buildRequestJson(caption, tagsText)
        );

        List<MultipartBody.Part> fileParts = new ArrayList<>();
        try {
            File imageFile = createUploadFile();
            if (imageFile != null) {
                RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
                fileParts.add(MultipartBody.Part.createFormData("files", imageFile.getName(), fileBody));
            }
        } catch (IOException e) {
            setLoading(false);
            Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            return;
        }

        apiService.createPost(requestPart, fileParts).enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostResponse> call,
                                   @NonNull Response<PostResponse> response) {
                setLoading(false);
                if (response.code() == 401) {
                    Toast.makeText(CreatePostActivity.this, R.string.msg_login_expired, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!response.isSuccessful()) {
                    Toast.makeText(CreatePostActivity.this, R.string.create_post_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(CreatePostActivity.this, R.string.create_post_success, Toast.LENGTH_SHORT).show();
                openFeedWithRefresh();
            }

            @Override
            public void onFailure(@NonNull Call<PostResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(
                        CreatePostActivity.this,
                        getString(R.string.msg_connection_error, t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private File createUploadFile() throws IOException {
        if (renderedBitmap != null) {
            File tempFile = new File(getCacheDir(), "post_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
                out.flush();
            }
            return tempFile;
        }
        if (selectedImageUri != null) {
            return createTempFileFromUri(selectedImageUri);
        }
        return null;
    }

    private String buildRequestJson(String caption, String rawTags) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"caption\":\"").append(escapeJson(caption)).append("\"");

        List<String> tags = parseTags(rawTags);
        if (!tags.isEmpty()) {
            json.append(",\"tagged_users\":[");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append("{\"user_id\":\"").append(escapeJson(tags.get(i))).append("\"}");
            }
            json.append("]");
        }

        json.append("}");
        return json.toString();
    }

    private List<String> parseTags(String rawTags) {
        List<String> result = new ArrayList<>();
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return result;
        }
        String[] pieces = rawTags.split("[,\\s]+");
        for (String piece : pieces) {
            String tag = piece.trim();
            if (tag.startsWith("@")) {
                tag = tag.substring(1);
            }
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return result;
    }

    private File createTempFileFromUri(Uri uri) throws IOException {
        String fileName = queryDisplayName(uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "upload_" + System.currentTimeMillis() + ".jpg";
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

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void styleSwitch(SwitchMaterial materialSwitch) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{
                ContextCompat.getColor(this, R.color.create_post_switch_thumb_on),
                ContextCompat.getColor(this, R.color.create_post_switch_thumb_off)
        };
        int[] trackColors = new int[]{
                ContextCompat.getColor(this, R.color.create_post_switch_track_on),
                ContextCompat.getColor(this, R.color.create_post_switch_track_off)
        };
        materialSwitch.setThumbTintList(new ColorStateList(states, thumbColors));
        materialSwitch.setTrackTintList(new ColorStateList(states, trackColors));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void setLoading(boolean loading) {
        binding.btnPost.setEnabled(!loading);
        binding.btnPost.setAlpha(loading ? 0.5f : 1f);
        binding.btnBack.setEnabled(!loading);
        binding.cardPreview.setEnabled(!loading);
        binding.btnEditImage.setEnabled(!loading);
        binding.cardLocation.setEnabled(!loading);
        binding.cardTagPeople.setEnabled(!loading);
        binding.switchFacebook.setEnabled(!loading);
        binding.switchTwitter.setEnabled(!loading);
        binding.btnPost.setText(loading
                ? getString(R.string.create_post_posting)
                : getString(R.string.create_post_post));
    }

    private void openFeedWithRefresh() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.putExtra(EXTRA_REFRESH_FEED, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
