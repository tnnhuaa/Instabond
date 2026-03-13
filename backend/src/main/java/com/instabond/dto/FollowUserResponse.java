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
@Schema(description = "Compact user info used in followers / following lists")
public class FollowUserResponse {

    @Schema(description = "User ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    @Schema(description = "Display name", example = "John Doe")
    private String full_name;

    @Schema(description = "Avatar URL", example = "https://res.cloudinary.com/instabond/image/upload/avatar.jpg")
    private String avatar_url;

    @Schema(description = "Relationship status from caller to this user", example = "accepted", allowableValues = {"pending", "accepted", "rejected"})
    private String relationship_status;
}
