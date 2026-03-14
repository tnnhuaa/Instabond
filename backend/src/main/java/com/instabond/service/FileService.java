package com.instabond.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.instabond.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif"
    );

    private final Cloudinary cloudinary;

    /**
     * Validates, uploads a single image to Cloudinary and returns a full {@link UploadResponse}.
     */
    public UploadResponse uploadImage(MultipartFile file) {
        validateFile(file);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "instabond")
            );

            String url       = getString(result, "secure_url");
            String publicId  = getString(result, "public_id");
            int    width     = getInt(result, "width");
            int    height    = getInt(result, "height");
            long   bytes     = getLong(result, "bytes");
            String format    = getString(result, "format");

            String mimeType = file.getContentType() != null
                    ? file.getContentType()
                    : "image/" + format;

            return UploadResponse.builder()
                    .url(url)
                    .public_id(publicId)
                    .mime_type(mimeType)
                    .size_bytes(bytes)
                    .width(width)
                    .height(height)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary: " + e.getMessage());
        }
    }

    public String uploadImageUrl(MultipartFile file) {
        return uploadImage(file).getUrl();
    }

    // Validation

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required and must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException(
                    "Invalid file type: " + contentType +
                    ". Allowed types: jpeg, png, webp, gif, heic, heif"
            );
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException(
                    "File too large: " + (file.getSize() / 1024 / 1024) + " MB. Maximum allowed size is 10 MB"
            );
        }
    }

    // Cloudinary result helpers

    private String getString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private int getInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private long getLong(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0L;
        try { return Long.parseLong(v.toString()); }
        catch (NumberFormatException e) { return 0L; }
    }
}