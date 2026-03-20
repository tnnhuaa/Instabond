package com.example.instabond_fe.view;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.databinding.ActivityCreatePostBinding;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

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

    private ActivityCreatePostBinding binding;
    private ApiService apiService;

    private Uri selectedImageUri;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);

        registerLaunchers();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnChooseImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnTakePhoto.setOnClickListener(v -> takePhotoLauncher.launch(null));
        binding.btnPost.setOnClickListener(v -> submitPost());
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            selectedImageUri = uri;
            Glide.with(this).load(uri).into(binding.ivPreview);
        });

        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                return;
            }
            selectedImageUri = saveBitmapToCache(bitmap);
            Glide.with(this).load(bitmap).into(binding.ivPreview);
        });
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        File file = new File(getCacheDir(), "captured_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Toast.makeText(this, "Khong luu duoc anh chup", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void submitPost() {
        String caption = binding.etCaption.getText().toString().trim();
        String tags = binding.etTags.getText().toString().trim();

        if (caption.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Nhap noi dung hoac chon anh", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Build JSON request body for the "request" part
        StringBuilder json = new StringBuilder("{");
        json.append("\"caption\":\"").append(escapeJson(caption)).append("\"");
        if (!tags.isEmpty()) {
            // Parse tags: split by comma or space, wrap as tagged_users
            String[] tagArr = tags.split("[,\\s]+");
            json.append(",\"tagged_users\":[");
            for (int i = 0; i < tagArr.length; i++) {
                String tag = tagArr[i].trim();
                if (tag.startsWith("@")) tag = tag.substring(1);
                if (!tag.isEmpty()) {
                    if (i > 0) json.append(",");
                    json.append("{\"user_id\":\"").append(escapeJson(tag)).append("\"}");
                }
            }
            json.append("]");
        }
        json.append("}");

        RequestBody requestPart = RequestBody.create(
                MediaType.parse("application/json"), json.toString());

        // Build file parts
        List<MultipartBody.Part> fileParts = new ArrayList<>();
        if (selectedImageUri != null) {
            try {
                File imageFile = createTempFileFromUri(selectedImageUri);
                if (imageFile != null) {
                    RequestBody fileBody = RequestBody.create(
                            MediaType.parse("image/*"), imageFile);
                    MultipartBody.Part part = MultipartBody.Part.createFormData(
                            "files", imageFile.getName(), fileBody);
                    fileParts.add(part);
                }
            } catch (IOException e) {
                Toast.makeText(this, "Khong doc duoc file anh", Toast.LENGTH_SHORT).show();
                setLoading(false);
                return;
            }
        }

        apiService.createPost(requestPart, fileParts).enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                setLoading(false);
                if (response.code() == 401) {
                    Toast.makeText(CreatePostActivity.this, "Phien dang nhap da het han", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!response.isSuccessful()) {
                    Toast.makeText(CreatePostActivity.this, "Dang bai that bai (code " + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(CreatePostActivity.this, "Dang bai thanh cong!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(CreatePostActivity.this, "Loi ket noi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void setLoading(boolean loading) {
        binding.btnPost.setEnabled(!loading);
        binding.btnChooseImage.setEnabled(!loading);
        binding.btnTakePhoto.setEnabled(!loading);
        binding.btnPost.setText(loading ? "Dang gui..." : "Dang bai");
    }
}
