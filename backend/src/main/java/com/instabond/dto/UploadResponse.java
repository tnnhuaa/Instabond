package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned after a successful file upload")
public class UploadResponse {

    @Schema(description = "Public HTTPS URL of the uploaded file on Cloudinary",
            example = "https://res.cloudinary.com/instabond/image/upload/v1700000000/instabond/abc123.jpg")
    private String url;

    @Schema(description = "Cloudinary public ID (can be used to delete / transform later)",
            example = "instabond/abc123")
    private String public_id;

    @Schema(description = "Detected MIME type of the uploaded file", example = "image/jpeg")
    private String mime_type;

    @Schema(description = "File size in bytes", example = "204800")
    private long size_bytes;

    @Schema(description = "Image width in pixels (0 if not applicable)", example = "1080")
    private int width;

    @Schema(description = "Image height in pixels (0 if not applicable)", example = "1350")
    private int height;
}

