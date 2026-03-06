package com.instabond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request body to update an existing post — only include fields you want to change")
public class UpdatePostRequest {

    @Schema(description = "Updated caption", example = "Updated caption")
    private String caption;

    @Schema(description = "Updated location tag")
    private LocationRequest location;

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
    }
}
