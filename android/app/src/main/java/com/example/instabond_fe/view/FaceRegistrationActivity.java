package com.example.instabond_fe.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityFaceRegistrationBinding;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FaceRegistrationActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private ActivityFaceRegistrationBinding binding;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;

    private final List<Bitmap> capturedFaces = new ArrayList<>();
    private static final int REQUIRED_IMAGES = 3;
    private boolean isProcessingApi = false;
    private long lastCaptureTime = 0;

    private ApiService apiService;
    private com.example.instabond_fe.network.SessionManager sessionManager;

    // --- State Machine ---
    private enum FaceStep {
        STRAIGHT(R.string.face_step_straight),
        LEFT(R.string.face_step_left),
        RIGHT(R.string.face_step_right),
        DONE(R.string.face_step_done);

        @StringRes
        final int instructionResId;

        FaceStep(@StringRes int instructionResId) {
            this.instructionResId = instructionResId;
        }
    }

    private FaceStep currentStep = FaceStep.STRAIGHT;
    // ---------------------------------------

    public static final String EXTRA_FACE_REGISTERED = "extra_face_registered";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFaceRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new com.example.instabond_fe.network.SessionManager(this);
        binding.btnClose.setOnClickListener(v -> finish());

        // First instruction
        updateFeedback(getString(currentStep.instructionResId));

        // Set up ML Kit Face Detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Detect eye open probability
                .build();
        faceDetector = FaceDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, this::processImageProxy);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            } catch (Exception exc) {
                Log.e("FaceReg", "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (isProcessingApi || capturedFaces.size() >= REQUIRED_IMAGES) {
            imageProxy.close();
            return;
        }

        // Convert ImageProxy to Bitmap
        Bitmap bitmap = imageProxy.toBitmap();

        if (bitmap != null) {
            InputImage image = InputImage.fromBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        evaluateFaces(faces, imageProxy);
                    })
                    .addOnFailureListener(e -> {
                        updateFeedback(getString(R.string.face_feedback_detection_error));
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void evaluateFaces(List<Face> faces, ImageProxy imageProxy) {
        if (faces.isEmpty()) {
            updateFeedback(getString(R.string.face_feedback_no_face));
            return;
        }
        if (faces.size() > 1) {
            updateFeedback(getString(R.string.face_feedback_single_face_only));
            return;
        }

        Face face = faces.get(0);

        // Check eye open probability
        if (face.getLeftEyeOpenProbability() != null && face.getRightEyeOpenProbability() != null) {
            if (face.getLeftEyeOpenProbability() < 0.7f || face.getRightEyeOpenProbability() < 0.7f) {
                updateFeedback(getString(R.string.face_feedback_open_eyes));
                return;
            }
        }

        // Check face size (not allow far away)
        float faceWidth = face.getBoundingBox().width();
        if (faceWidth < imageProxy.getWidth() * 0.35) {
            updateFeedback(getString(R.string.face_feedback_move_closer));
            return;
        }

        // Check Euler Angles by State Machine
        float rotY = face.getHeadEulerAngleY();
        float rotZ = face.getHeadEulerAngleZ();

        boolean isCorrectAngle = false;

        switch (currentStep) {
            case STRAIGHT:
                float faceCenterX = face.getBoundingBox().exactCenterX();
                float faceCenterY = face.getBoundingBox().exactCenterY();

                float imageCenterX = imageProxy.getWidth() / 2f;
                float imageCenterY = imageProxy.getHeight() / 2f;

                // Limit tolerance
                float toleranceX = imageProxy.getWidth() * 0.15f;
                float toleranceY = imageProxy.getHeight() * 0.15f;

                boolean isCentered = Math.abs(faceCenterX - imageCenterX) <= toleranceX &&
                        Math.abs(faceCenterY - imageCenterY) <= toleranceY;

                if (isCentered) {
                    if (Math.abs(rotY) <= 10 && Math.abs(rotZ) <= 10) {
                        isCorrectAngle = true;
                    } else {
                        updateFeedback(getString(currentStep.instructionResId));
                    }
                } else {
                    updateFeedback(getString(R.string.face_feedback_center_face));
                }
                break;
            case LEFT:
                // Turn left: rotY > 15 degrees (positive) and rotZ within ±15 degrees
                if (rotY > 15 && Math.abs(rotZ) <= 15) {
                    isCorrectAngle = true;
                } else {
                    updateFeedback(getString(currentStep.instructionResId));
                }
                break;
            case RIGHT:
                // Turn right: rotY < -15 degrees (negative) and rotZ within ±15 degrees
                if (rotY < -15 && Math.abs(rotZ) <= 15) {
                    isCorrectAngle = true;
                } else {
                    updateFeedback(getString(currentStep.instructionResId));
                }
                break;
            case DONE:
                return;
        }

        if (isCorrectAngle) {
            long currentTime = System.currentTimeMillis();
            // Keep still for 1000ms to avoid blurry capture and give user time to read the instruction
            if (currentTime - lastCaptureTime > 1000) {

                Bitmap bitmap = imageProxy.toBitmap();

                // Crop face
                Bitmap faceOnlyBitmap = cropFace(bitmap, face.getBoundingBox(), imageProxy.getImageInfo().getRotationDegrees());

                capturedFaces.add(faceOnlyBitmap);
                lastCaptureTime = currentTime;

                // Move to next step or submit if done
                if (currentStep == FaceStep.STRAIGHT) {
                    currentStep = FaceStep.LEFT;
                    updateFeedback(getString(R.string.face_feedback_good_now_step, getString(currentStep.instructionResId)));
                } else if (currentStep == FaceStep.LEFT) {
                    currentStep = FaceStep.RIGHT;
                    updateFeedback(getString(R.string.face_feedback_good_now_step, getString(currentStep.instructionResId)));
                } else if (currentStep == FaceStep.RIGHT) {
                    currentStep = FaceStep.DONE;
                    updateFeedback(getString(currentStep.instructionResId));
                    isProcessingApi = true;
                    submitFaces();
                }
            } else {
                updateFeedback(getString(R.string.face_feedback_hold_still));
            }
        }
    }

    private void updateFeedback(String message) {
        runOnUiThread(() -> binding.tvFeedback.setText(message));
    }

    private void submitFaces() {
        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.tvFeedback.setText(getString(R.string.face_feedback_processing_upload));
        });

        List<MultipartBody.Part> parts = new ArrayList<>();

        for (int i = 0; i < capturedFaces.size(); i++) {
            Bitmap bmp = capturedFaces.get(i);
            File file = bitmapToFile(bmp, "face_img_" + i + ".jpg");
            if (file != null) {
                RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
                // "images" là field name trong form-data API
                parts.add(MultipartBody.Part.createFormData("images", file.getName(), requestBody));
            }
        }

        // Send API
        apiService.registerFace(parts).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
                if (response.isSuccessful()) {
                    Toast.makeText(FaceRegistrationActivity.this, getString(R.string.face_register_success), Toast.LENGTH_SHORT).show();
                    android.content.Intent resultIntent = new android.content.Intent();
                    resultIntent.putExtra(EXTRA_FACE_REGISTERED, true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    try {
                        Log.e("FaceReg", "Server error: " + response.code() + " - " + response.errorBody().string());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    handleError();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
                Log.e("FaceReg", "Internet error/call API failed: " + t.getMessage());
                handleError();
            }
        });
    }

    private void handleError() {
        if (sessionManager.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.face_register_failed_retry), Toast.LENGTH_SHORT).show();
        }
        capturedFaces.clear();
        isProcessingApi = false;
        currentStep = FaceStep.STRAIGHT;
        updateFeedback(getString(currentStep.instructionResId));
    }

    // --- Utility Methods ---

    private Bitmap cropFace(Bitmap original, android.graphics.Rect boundingBox, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap uprightBitmap = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);

        // Add 20% padding around the face
        int paddingX = (int) (boundingBox.width() * 0.2f);
        int paddingY = (int) (boundingBox.height() * 0.2f);

        int x = Math.max(0, boundingBox.left - paddingX);
        int y = Math.max(0, boundingBox.top - paddingY);
        int width = Math.min(uprightBitmap.getWidth() - x, boundingBox.width() + 2 * paddingX);
        int height = Math.min(uprightBitmap.getHeight() - y, boundingBox.height() + 2 * paddingY);

        Bitmap croppedFace = Bitmap.createBitmap(uprightBitmap, x, y, width, height);

        // Flip horizontally to correct mirror effect from front camera
        Matrix mirrorMatrix = new Matrix();
        mirrorMatrix.postScale(-1f, 1f, croppedFace.getWidth() / 2f, croppedFace.getHeight() / 2f);

        return Bitmap.createBitmap(croppedFace, 0, 0, croppedFace.getWidth(), croppedFace.getHeight(), mirrorMatrix, true);
    }

    private File bitmapToFile(Bitmap bitmap, String fileName) {
        File f = new File(getCacheDir(), fileName);
        try {
            f.createNewFile();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            byte[] bitmapdata = bos.toByteArray();

            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
            return f;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.face_register_camera_permission_required), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}
