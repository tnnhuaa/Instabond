package com.instabond.dto;

import com.instabond.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request body to update an existing post - only include fields you want to change")
public class UpdatePostRequest {

    @Size(max = 2200, message = "caption must be at most 2200 characters")
    @Schema(description = "Updated caption", example = "Updated caption")
    private String caption;

    @Valid
    @Schema(description = "Updated location tag")
    private LocationRequest location;

    @Valid
    @Schema(description = "Updated list of tagged users")
    private List<TaggedUserRequest> tagged_users;

    @Data
    @Schema(description = "Location tag")
    public static class LocationRequest {
        @Schema(example = "Hoi An, Vietnam")
        private String name;
        @Schema(description = "[longitude, latitude]", example = "[108.3380, 15.8801]")
        private List<Double> coordinates;
    }

    @Data
    @Schema(description = "Tagged user")
    public static class TaggedUserRequest {
        @Schema(description = "ID of the tagged user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
        private String user_id;

        @Schema(description = "The tagging method (either automatically by AI or manually by a user)",
                allowableValues = {"auto-ai", "user-tag"},
                example = "auto-ai")
        private String tag_type;

        @Schema(description = "Confidence score of the AI detection (ranging from 0.0 to 1.0)", example = "0.77")
        private double confidence;

        @Schema(description = "The coordinate position of the tag on the media")
        private Post.TaggedUser.Position position;
    }
}
