package com.example.instabond_fe.view;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityCreateStoryBinding;
import com.example.instabond_fe.model.StoryResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.utils.AvatarFrameResolver;
import com.example.instabond_fe.utils.AvatarLoader;

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
    public static final String EXTRA_REFRESH_STORIES = "refresh_stories";

    private static final int MAX_SOURCE_EDGE = 1600;

    private ActivityCreateStoryBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;

    private Uri selectedImageUri;
    private Bitmap selectedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateStoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyComposerAvatarFrame();

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        registerLaunchers();
        setupToolbar();
        setupActions();
        loadCurrentUser();
        renderPreview();
    }

    private void applyComposerAvatarFrame() {
        AvatarFrameResolver.AvatarFrame frame = AvatarFrameResolver.resolveCreateStoryComposerAvatarFrame();
        binding.storyComposerAvatarRing.setBackgroundResource(frame.ringBackgroundRes);
        binding.ivStoryComposerAvatar.setBackgroundResource(frame.avatarBackgroundRes);
        int paddingPx = Math.round(getResources().getDisplayMetrics().density * frame.ringPaddingDp);
        binding.storyComposerAvatarRing.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    }

    private void setupToolbar() {
        binding.btnBackStoryComposer.setOnClickListener(v -> finish());
        binding.btnShareStory.setOnClickListener(v -> submitStory());
        binding.btnStorySettings.setOnClickListener(v ->
                Toast.makeText(this, R.string.story_create_tools_soon, Toast.LENGTH_SHORT).show()
        );
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
        binding.btnStoryToolText.setOnClickListener(toolsSoonClick);
        binding.btnStoryToolComment.setOnClickListener(toolsSoonClick);
        binding.btnStoryToolMusic.setOnClickListener(toolsSoonClick);
        binding.btnStoryToolEffects.setOnClickListener(toolsSoonClick);
        binding.btnStoryToolMore.setOnClickListener(toolsSoonClick);
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            selectedImageUri = uri;
            try {
                selectedBitmap = decodeBitmap(uri);
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
        binding.previewCard.setClickable(hasImage);
        binding.previewCard.setFocusable(hasImage);
        binding.storyPreviewBottomScrim.setAlpha(hasImage ? 0.9f : 0.65f);

        if (!hasImage) {
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
                    if (response.code() == 401) {
                        Toast.makeText(CreateStoryActivity.this, R.string.msg_login_expired, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!response.isSuccessful()) {
                        Toast.makeText(CreateStoryActivity.this, R.string.story_create_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(CreateStoryActivity.this, R.string.story_create_success, Toast.LENGTH_SHORT).show();
                    openFeedWithRefresh();
                }

                @Override
                public void onFailure(@NonNull Call<StoryResponse> call, @NonNull Throwable t) {
                    setLoading(false);
                    Toast.makeText(
                            CreateStoryActivity.this,
                            getString(R.string.msg_connection_error, t.getMessage()),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        } catch (IOException e) {
            setLoading(false);
            Toast.makeText(this, R.string.story_create_image_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File createUploadFile() throws IOException {
        if (selectedBitmap != null) {
            File tempFile = new File(getCacheDir(), "story_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                selectedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
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
        binding.btnStoryToolMusic.setEnabled(!loading);
        binding.btnStoryToolEffects.setEnabled(!loading);
        binding.btnStoryToolMore.setEnabled(!loading);
        binding.btnShareStory.setText(loading
                ? getString(R.string.story_create_posting)
                : getString(R.string.story_create_post_action));
    }

    private void openFeedWithRefresh() {
        Intent intent = new Intent(this, NewsfeedActivity.class);
        intent.putExtra(EXTRA_REFRESH_STORIES, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
