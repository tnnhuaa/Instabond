package com.example.instabond_fe.view;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.instabond_fe.databinding.ActivityCreatePostBinding;
import com.example.instabond_fe.model.CreatePostRequest;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreatePostActivity extends AppCompatActivity {

    private ActivityCreatePostBinding binding;
    private ApiService apiService;

    private Uri selectedImageUri;
    private int mediaWidth = 0;
    private int mediaHeight = 0;

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
            mediaWidth = 0;
            mediaHeight = 0;
            Glide.with(this).load(uri).into(binding.ivPreview);
        });

        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                return;
            }
            selectedImageUri = saveBitmapToCache(bitmap);
            mediaWidth = bitmap.getWidth();
            mediaHeight = bitmap.getHeight();
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
        if (caption.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Nhap noi dung hoac chon anh", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        String mediaUrl = selectedImageUri == null ? null : selectedImageUri.toString();
        CreatePostRequest request = CreatePostRequest.fromCaptionAndMedia(caption, mediaUrl, mediaWidth, mediaHeight);

        apiService.createPost(request).enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                setLoading(false);
                if (response.code() == 401) {
                    Toast.makeText(CreatePostActivity.this, "Phien dang nhap da het han", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!response.isSuccessful()) {
                    Toast.makeText(CreatePostActivity.this, "Dang bai that bai", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(CreatePostActivity.this, "Dang bai thanh cong", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(CreatePostActivity.this, "Loi ket noi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnPost.setEnabled(!loading);
        binding.btnChooseImage.setEnabled(!loading);
        binding.btnTakePhoto.setEnabled(!loading);
        binding.btnPost.setText(loading ? "Dang gui..." : "Dang bai");
    }
}

