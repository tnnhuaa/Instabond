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
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityCreatePostBinding;
import com.example.instabond_fe.model.CreatePostRequest;
import com.example.instabond_fe.model.MusicSuggestion;
import com.example.instabond_fe.model.PostSuggestionResponse;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.SuggestedTag;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.AvatarLoader;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreatePostActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private static final String EXTRA_REFRESH_FEED = "refresh_feed";

    public enum FilterType {
        NORMAL,
        VIVID,
        BW,
        VINTAGE,
        PULSE
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
    private ActivityResultLauncher<Intent> editImageLauncher;

    private Uri selectedImageUri;
    private Bitmap sourceBitmap;
    private Bitmap renderedBitmap;

    private FilterType activeFilter = FilterType.NORMAL;

    private String tagsText = "";
    private String locationText = "";
    private String aiSceneDescription = "";

    private MusicSuggestion selectedMusic;
    private final List<MusicSuggestion> aiMusicSuggestions = new ArrayList<>();
    private boolean isFetchingAiSuggestions = false;
    private boolean isSubmittingPost = false;
    private Call<PostSuggestionResponse> postSuggestionCall;
    private ArrayList<SuggestedTag> taggedUsersList = new ArrayList<>();
    private ActivityResultLauncher<Intent> tagUserLauncher;

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
        updateAiSuggestionUiState();
        updateOptionSummaries();
        renderEditorState();
        loadCurrentUser();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnPost.setOnClickListener(v -> submitPost());
    }

    private void setupBottomNav() {
        binding.bottomNav.bind(this, InstaBottomNavView.Tab.CREATE);
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
                openImageEditorWithCurrentPreview();
            }
        });
        binding.btnEditImage.setOnClickListener(v -> openImageEditorWithCurrentPreview());
        binding.cardLocation.setOnClickListener(v -> showTextInputDialog(
                getString(R.string.create_post_dialog_location),
                getString(R.string.create_post_dialog_hint_location),
                locationText,
                value -> {
                    locationText = value;
                    updateOptionSummaries();
                }));
        binding.cardTagPeople.setOnClickListener(v -> {
            if (sourceBitmap == null) {
                Toast.makeText(this, R.string.create_post_select_photo_first, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isFetchingAiSuggestions) {
                Toast.makeText(this, getString(R.string.create_post_status_scanning_users_in_image), Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                File tempImage = createUploadFile();
                if (tempImage != null) {
                    Intent intent = new Intent(this, TagUserActivity.class);
                    intent.putExtra("IMAGE_URI", Uri.fromFile(tempImage).toString());
                    intent.putExtra("TAGGED_USERS", taggedUsersList);
                    tagUserLauncher.launch(intent);
                }
            } catch (IOException e) {
                Toast.makeText(this, getString(R.string.create_post_error_process_image), Toast.LENGTH_SHORT).show();
            }
        });
        binding.cardMusic.setOnClickListener(v -> showMusicSelectionDialog());
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            selectedImageUri = uri;
            try {
                sourceBitmap = decodeBitmap(uri);
                resetEdits(false);
                renderEditorState();
                openImageEditor(uri);
            } catch (IOException e) {
                Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            }
        });

        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) return;
            selectedImageUri = saveBitmapToCacheUri(bitmap);
            sourceBitmap = limitBitmapSize(bitmap, MAX_SOURCE_EDGE);
            resetEdits(false);
            renderEditorState();

            if (selectedImageUri != null) {
                openImageEditor(selectedImageUri);
            }
        });

        editImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        android.util.Log.d("AI_DEBUG", "Editor canceled. Fetching suggestions for original image.");
                        if (sourceBitmap != null) requestAiSuggestions();
                        return;
                    }

                    String outputUri = result.getData().getStringExtra(ImageEditorActivity.EXTRA_OUTPUT_URI);
                    if (outputUri == null || outputUri.trim().isEmpty()) {
                        android.util.Log.d("AI_DEBUG", "Edited output URI is empty. Fetching suggestions for original image.");
                        if (sourceBitmap != null) requestAiSuggestions();
                        return;
                    }

                    selectedImageUri = Uri.parse(outputUri);
                    try {
                        sourceBitmap = decodeBitmap(selectedImageUri);
                        resetEdits(false);
                        renderEditorState();
                        android.util.Log.d("AI_DEBUG", "Editor done. Fetching suggestions for EDITED image.");
                        requestAiSuggestions();
                    } catch (IOException e) {
                        Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
                    }
                });

        tagUserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Object serializable = result.getData().getSerializableExtra("TAGGED_USERS");
                        if (serializable instanceof ArrayList<?>) {
                            taggedUsersList = (ArrayList<SuggestedTag>) serializable;
                        } else {
                            taggedUsersList = new ArrayList<>();
                        }
                        updateOptionSummaries();
                    }
                });
    }

    private void loadCurrentUser() {
        String userId = sessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            AvatarLoader.load(binding.ivUserAvatar, null);
            return;
        }

        apiService.getUserProfile(userId).enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call,
                                   @NonNull Response<UserProfileResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    AvatarLoader.load(binding.ivUserAvatar, null);
                    return;
                }

                String avatarUrl = response.body().getAvatarUrl();
                AvatarLoader.load(binding.ivUserAvatar, avatarUrl);
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                AvatarLoader.load(binding.ivUserAvatar, null);
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
                    if (which == 0) pickImageLauncher.launch("image/*");
                    else takePhotoLauncher.launch(null);
                })
                .setNegativeButton(R.string.create_post_cancel, null)
                .show();
    }

    private void openImageEditorWithCurrentPreview() {
        if (sourceBitmap == null) {
            showImageSourceDialog();
            return;
        }

        Uri editorSource = renderedBitmap != null
                ? saveBitmapToCacheUri(renderedBitmap)
                : selectedImageUri;
        if (editorSource == null) {
            Toast.makeText(this, R.string.create_post_image_read_error, Toast.LENGTH_SHORT).show();
            return;
        }
        openImageEditor(editorSource);
    }

    private void openImageEditor(Uri sourceUri) {
        Intent intent = new Intent(this, ImageEditorActivity.class);
        intent.putExtra(ImageEditorActivity.EXTRA_INPUT_URI, sourceUri.toString());
        editImageLauncher.launch(intent);
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

        String musicSummary = null;
        if (selectedMusic != null) {
            musicSummary = getString(
                    R.string.create_post_music_summary,
                    safe(selectedMusic.getSongName()),
                    safe(selectedMusic.getArtist())
            );
        } else if (isFetchingAiSuggestions) {
            musicSummary = getString(R.string.create_post_music_summary_loading);
        } else if (!aiMusicSuggestions.isEmpty()) {
            musicSummary = getString(R.string.create_post_music_summary_ready, aiMusicSuggestions.size());
        }

        binding.tvMusicValue.setText(musicSummary);
        binding.tvMusicValue.setVisibility(TextUtils.isEmpty(musicSummary) ? View.GONE : View.VISIBLE);

        if (isFetchingAiSuggestions) {
            binding.tvTagsValue.setVisibility(View.VISIBLE);
            binding.tvTagsValue.setText(getString(R.string.create_post_status_scanning_users_in_image));
            binding.rvTaggedUsersPreview.setVisibility(View.GONE);
        } else {
            binding.tvTagsValue.setVisibility(View.GONE);
            if (taggedUsersList.isEmpty()) {
                binding.rvTaggedUsersPreview.setVisibility(View.GONE);
            } else {
                binding.rvTaggedUsersPreview.setVisibility(View.VISIBLE);

                if (binding.rvTaggedUsersPreview.getAdapter() == null) {
                    binding.rvTaggedUsersPreview.setLayoutManager(
                            new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    );

                    TaggedUserAdapter adapter = new TaggedUserAdapter(false);
                    binding.rvTaggedUsersPreview.setAdapter(adapter);
                }
                ((TaggedUserAdapter) binding.rvTaggedUsersPreview.getAdapter()).setTaggedUsers(taggedUsersList);
            }
        }
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

        renderedBitmap = buildOutputBitmap(sourceBitmap, activeFilter);
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
        if (sourceBitmap == null) return null;
        Bitmap square = cropCenterSquare(sourceBitmap);
        Bitmap scaled = Bitmap.createScaledBitmap(square, FILTER_THUMBNAIL_SIZE, FILTER_THUMBNAIL_SIZE, true);
        return applyFilterToBitmap(scaled, filterType);
    }

    private Bitmap buildOutputBitmap(Bitmap original, FilterType filterType) {
        return applyFilterToBitmap(original, filterType);
    }

    private Bitmap applyFilterToBitmap(Bitmap source, FilterType filterType) {
        if (source == null) return null;
        switch (filterType) {
            case VIVID: return applyColorMatrix(source, buildVividMatrix());
            case BW: return applyColorMatrix(source, buildBwMatrix());
            case VINTAGE: return applyColorMatrix(source, buildVintageMatrix());
            case PULSE: return applyPulseFilter(source);
            case NORMAL:
            default: return copyBitmap(source);
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

    private Bitmap cropCenterSquare(Bitmap source) {
        if (source == null) return null;
        int size = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        return Bitmap.createBitmap(source, left, top, size, size);
    }

    private Bitmap copyBitmap(Bitmap source) {
        if (source == null) return null;
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
        if (bitmap == null) return null;
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

    private void resetEdits(boolean rerender) {
        activeFilter = FilterType.NORMAL;
        if (rerender) renderEditorState();
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

        List<CreatePostRequest.TaggedUserRequest> mappedTaggedUsers = new ArrayList<>();
        for (SuggestedTag tag : taggedUsersList) {
            if (tag == null) continue;

            String userId = safe(tag.getId());
            if (userId.isEmpty()) continue;

            String tagType = normalizeTagType(tag);
            Double confidence = normalizeConfidence(tag.getConfidence(), tagType);

            Double x = 0.5;
            Double y = 0.5;
            if (tag.getPosition() != null) {
                x = normalizeCoordinate(tag.getPosition().getX());
                y = normalizeCoordinate(tag.getPosition().getY());
            }

            CreatePostRequest.TaggedUserRequest.Position pos =
                    new CreatePostRequest.TaggedUserRequest.Position(x, y);

            mappedTaggedUsers.add(new CreatePostRequest.TaggedUserRequest(
                    userId,
                    tagType,
                    confidence,
                    pos
            ));
        }

        CreatePostRequest request = CreatePostRequest.fromCaptionAndMedia(
                caption,
                null,
                0, 0,
                mappedTaggedUsers,
                locationText != null && !locationText.trim().isEmpty()
                        ? new CreatePostRequest.LocationRequest(locationText.trim(), null)
                        : null,
                selectedMusic != null
                        ? new CreatePostRequest.MusicSuggestionRequest(
                        selectedMusic.getSongName(),
                        selectedMusic.getArtist(),
                        selectedMusic.getPreviewUrl())
                        : null
        );

        String requestJson = new Gson().toJson(request);
        RequestBody requestPart = RequestBody.create(
                MediaType.parse("application/json"),
                requestJson
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

    private File createTempFileFromUri(Uri uri) throws IOException {
        String fileName = queryDisplayName(uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "upload_" + System.currentTimeMillis() + ".jpg";
        }

        File tempFile = new File(getCacheDir(), fileName);
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile, false)) {
            if (inputStream == null) return null;
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
                if (idx >= 0) return cursor.getString(idx);
            }
        }
        return null;
    }

    private String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void showMusicSelectionDialog() {
        List<MusicSuggestion> options = buildMusicOptions();
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.create_post_select_photo_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFetchingAiSuggestions) {
            Toast.makeText(this, R.string.create_post_music_summary_loading, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            MusicSuggestion suggestion = options.get(i);
            String prefix = suggestion.isAiRecommended() ? "\u2728 AI: " : "";
            labels[i] = prefix + safe(suggestion.getSongName()) + " - " + safe(suggestion.getArtist());
        }

        int checkedIndex = findSelectedMusicIndex(options);
        final int[] pendingIndex = {checkedIndex};

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_post_dialog_music)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> pendingIndex[0] = which)
                .setNegativeButton(R.string.create_post_cancel, null)
                .setPositiveButton(R.string.create_post_apply, (dialog, which) -> {
                    if (pendingIndex[0] >= 0 && pendingIndex[0] < options.size()) {
                        selectedMusic = options.get(pendingIndex[0]);
                        updateOptionSummaries();
                    }
                });

        if (selectedMusic != null) {
            builder.setNeutralButton(R.string.create_post_music_clear, (dialog, which) -> {
                selectedMusic = null;
                updateOptionSummaries();
            });
        }

        builder.show();
    }

    private int findSelectedMusicIndex(List<MusicSuggestion> options) {
        if (selectedMusic == null) return -1;
        String selectedKey = musicKey(selectedMusic);
        for (int i = 0; i < options.size(); i++) {
            if (selectedKey.equals(musicKey(options.get(i)))) return i;
        }
        return -1;
    }

    private List<MusicSuggestion> buildMusicOptions() {
        Map<String, MusicSuggestion> merged = new LinkedHashMap<>();

        for (MusicSuggestion suggestion : aiMusicSuggestions) {
            if (suggestion == null) continue;
            suggestion.setAiRecommended(true);
            merged.put(musicKey(suggestion), suggestion);
        }

        for (MusicSuggestion suggestion : getManualMusicCatalog()) {
            if (suggestion == null) continue;
            merged.putIfAbsent(musicKey(suggestion), suggestion);
        }

        if (selectedMusic != null) {
            merged.putIfAbsent(musicKey(selectedMusic), selectedMusic);
        }

        return new ArrayList<>(merged.values());
    }

    private List<MusicSuggestion> getManualMusicCatalog() {
        List<MusicSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new MusicSuggestion("Golden Hour", "JVKE", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959427/JVKE_-_golden_hour_official_music_video_liyfj6.mp3", null, false));
        suggestions.add(new MusicSuggestion("Sunset Lover", "Petit Biscuit", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959426/Petit_Biscuit_-_Sunset_Lover_Music_Video_psnf6b.mp3", null, false));
        suggestions.add(new MusicSuggestion("Sunflower", "Post Malone, Swae Lee", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959426/Post_Malone_Swae_Lee_-_Sunflower_Spider-Man__Into_the_Spider-Verse_c6b4di.mp3", null, false));
        suggestions.add(new MusicSuggestion("Night Changes", "One Direction", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959428/One_Direction_-_Night_Changes_xbfzdr.mp3", null, false));
        suggestions.add(new MusicSuggestion("Midnight City", "M83", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959427/M83_Midnight_City_Official_video_zi20yk.mp3", null, false));
        suggestions.add(new MusicSuggestion("Until I Found You", "Stephen Sanchez", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959427/Stephen_Sanchez_-_Until_I_Found_You_Official_Video_lhfljn.mp3", null, false));
        suggestions.add(new MusicSuggestion("Ocean Eyes", "Billie Eilish", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959427/Billie_Eilish_-_ocean_eyes_Official_Music_Video_kbaohv.mp3", null, false));
        suggestions.add(new MusicSuggestion("Adventure of a Lifetime", "Coldplay", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959429/Coldplay_-_Adventure_Of_A_Lifetime_Official_Video_wrrj5v.mp3", null, false));
        suggestions.add(new MusicSuggestion("Good Days", "SZA", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959429/SZA_-_Good_Days_Audio_uu6zhv.mp3", null, false));
        suggestions.add(new MusicSuggestion("Blinding Lights", "The Weeknd", "https://res.cloudinary.com/dlmpl2oi1/video/upload/v1776959428/The_Weeknd_-_Blinding_Lights_Official_Video_exokxh.mp3", null, false));
        return suggestions;
    }

    private String musicKey(MusicSuggestion suggestion) {
        String songName = suggestion != null ? safe(suggestion.getSongName()) : "";
        String artist = suggestion != null ? safe(suggestion.getArtist()) : "";
        return (songName + "|" + artist).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTagType(SuggestedTag tag) {
        String raw = safe(tag.getTagType()).toLowerCase(Locale.ROOT);
        if ("auto-ai".equals(raw) || "user-tag".equals(raw)) return raw;
        return tag.getConfidence() != null ? "auto-ai" : "user-tag";
    }

    private double normalizeConfidence(Double confidence, String tagType) {
        if ("user-tag".equals(tagType)) return 1.0;
        double value = confidence != null ? confidence : 0.0;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private double normalizeCoordinate(Double coordinate) {
        double value = coordinate != null ? coordinate : 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private void requestAiSuggestions() {
        if (postSuggestionCall != null) {
            postSuggestionCall.cancel();
            postSuggestionCall = null;
        }

        aiMusicSuggestions.clear();
        aiSceneDescription = "";

        File imageFile;
        try {
            imageFile = createUploadFile();
        } catch (IOException e) {
            isFetchingAiSuggestions = false;
            updateAiSuggestionUiState();
            updateOptionSummaries();
            return;
        }

        if (imageFile == null) {
            isFetchingAiSuggestions = false;
            updateAiSuggestionUiState();
            updateOptionSummaries();
            return;
        }

        isFetchingAiSuggestions = true;
        updateAiSuggestionUiState();
        updateOptionSummaries();

        RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", imageFile.getName(), fileBody);

        postSuggestionCall = apiService.getPostSuggestions(imagePart);
        postSuggestionCall.enqueue(new Callback<PostSuggestionResponse>() {
            @Override
            public void onResponse(@NonNull Call<PostSuggestionResponse> call,
                                   @NonNull Response<PostSuggestionResponse> response) {
                if (call.isCanceled()) return;

                isFetchingAiSuggestions = false;
                updateAiSuggestionUiState();
                aiMusicSuggestions.clear();

                if (response.isSuccessful() && response.body() != null) {
                    // LOG 1

                    List<SuggestedTag> apiTags = response.body().getSuggestedTags();
                    android.util.Log.d("AI_DEBUG", "--------------------------------------------------");
                    android.util.Log.d("AI_DEBUG", "[LOG 1] The number of tag Retrofit: " + (apiTags != null ? apiTags.size() : "null"));
                    if (apiTags != null) {
                        for (int i = 0; i < apiTags.size(); i++) {
                            SuggestedTag t = apiTags.get(i);
                            android.util.Log.d("AI_DEBUG", "  -> Raw Tag [" + i + "]: username=" + t.getUsername() + ", conf=" + t.getConfidence() + ", id=" + t.getId());
                        }
                    }

                    aiSceneDescription = safe(response.body().getSceneDescription());

                    if (response.body().getMusicSuggestions() != null) {
                        for (MusicSuggestion suggestion : response.body().getMusicSuggestions()) {
                            if (suggestion != null) {
                                suggestion.setAiRecommended(true);
                                aiMusicSuggestions.add(suggestion);
                            }
                        }
                    }

                    if (response.body().getSuggestedTags() != null) {
                        java.util.Iterator<SuggestedTag> iterator = taggedUsersList.iterator();
                        while (iterator.hasNext()) {
                            SuggestedTag tag = iterator.next();
                            if ("auto-ai".equals(tag.getTagType())) {
                                iterator.remove();
                            }
                        }

                        if (!response.body().getSuggestedTags().isEmpty()) {
                            List<String> autoTags = new ArrayList<>();

                            for (SuggestedTag suggestedTag : response.body().getSuggestedTags()) {
                                if (suggestedTag == null) continue;

                                String username = safe(suggestedTag.getUsername());
                                if (!username.isEmpty()) autoTags.add("@" + username);

                                boolean isAlreadyTagged = false;
                                for (int i = 0; i < taggedUsersList.size(); i++) {
                                    SuggestedTag existingTag = taggedUsersList.get(i);
                                    if (existingTag.getId() != null && existingTag.getId().equals(suggestedTag.getId())) {
                                        isAlreadyTagged = true;
                                        suggestedTag.setTagType(existingTag.getTagType());
                                        if (suggestedTag.getConfidence() == null) {
                                            suggestedTag.setConfidence(existingTag.getConfidence());
                                        }
                                        taggedUsersList.set(i, suggestedTag);
                                        break;
                                    }
                                }

                                if (!isAlreadyTagged) {
                                    suggestedTag.setTagType("auto-ai");
                                    if (suggestedTag.getConfidence() == null) {
                                        suggestedTag.setConfidence(0.0);
                                    }
                                    taggedUsersList.add(suggestedTag);
                                }
                            }

                            if (!autoTags.isEmpty()) {
                                tagsText = TextUtils.join(" ", autoTags);
                            }
                        }
                    }

                    // LOG 2

                    android.util.Log.d("AI_DEBUG", "[LOG 2] The number of tag in list before drawing UI: " + taggedUsersList.size());
                    for (int i = 0; i < taggedUsersList.size(); i++) {
                        SuggestedTag t = taggedUsersList.get(i);
                        android.util.Log.d("AI_DEBUG", "  -> Final Tag [" + i + "]: username=" + t.getUsername() + ", type=" + t.getTagType());
                    }
                    android.util.Log.d("AI_DEBUG", "--------------------------------------------------");
                }
                updateOptionSummaries();
            }

            @Override
            public void onFailure(@NonNull Call<PostSuggestionResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;

                isFetchingAiSuggestions = false;
                updateAiSuggestionUiState();
                aiMusicSuggestions.clear();
                aiSceneDescription = "";
                updateOptionSummaries();
            }
        });
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
        isSubmittingPost = loading;
        binding.btnBack.setEnabled(!loading);
        binding.cardPreview.setEnabled(!loading);
        binding.btnEditImage.setEnabled(!loading);
        binding.cardLocation.setEnabled(!loading);
        binding.cardTagPeople.setEnabled(!loading);
        binding.cardMusic.setEnabled(!loading);
        binding.switchFacebook.setEnabled(!loading);
        binding.switchTwitter.setEnabled(!loading);
        binding.btnPost.setText(loading
                ? getString(R.string.create_post_posting)
                : getString(R.string.create_post_post));
        updateAiSuggestionUiState();
    }

    private void updateAiSuggestionUiState() {
        binding.pbAiLoading.setVisibility(isFetchingAiSuggestions ? View.VISIBLE : View.GONE);
        boolean enablePost = !isSubmittingPost && !isFetchingAiSuggestions;
        binding.btnPost.setEnabled(enablePost);
        binding.btnPost.setAlpha(enablePost ? 1f : 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (postSuggestionCall != null) postSuggestionCall.cancel();
        super.onDestroy();
    }

    private void openFeedWithRefresh() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.putExtra(EXTRA_REFRESH_FEED, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
