package com.instabond.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.instabond.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full user profile information")
public class ProfileResponse {
    @Schema(description = "User ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
    private String id;
    @Schema(description = "Unique username", example = "john_doe")
    private String username;
    @Schema(description = "Display name", example = "John Doe")
    private String full_name;
    @Schema(description = "Short bio / about me", example = "Coffee lover")
    private String bio;
    @Schema(description = "Avatar URL", example = "https://res.cloudinary.com/instabond/image/upload/avatar.jpg")
    private String avatar_url;
    @Schema(description = "Phone number", example = "0912345678")
    private String phone_number;
    @Schema(description = "Total number of posts published by this user", example = "42")
    private long posts_count;
    @Schema(description = "Number of followers", example = "1200")
    private long followers_count;
    @Schema(description = "Number of users this account is following", example = "300")
    private long following_count;
    @Schema(description = "Whether this account is private", example = "false")
    @JsonProperty("is_private")
    private boolean is_private;
    @Schema(description = "Badges earned by this user")
    private List<User.Badge> badges;
    @Schema(description = "Account settings (tagging permission, theme, etc.)")
    private User.Setting settings;
    @Schema(description = "Account creation timestamp (UTC)", example = "2024-01-15T08:30:00Z")
    private Instant created_at;

    @Schema(description = "Relationship status from caller to this user (none, pending, accepted)", example = "accepted")
    private String relationship_status;

    @Schema(description = "True if both users follow each other", example = "true")
    private boolean is_mutual_follow;

    public void setRelationship_status(String relationship_status) {
        this.relationship_status = relationship_status;
    }

    public String getRelationship_status() {
        return relationship_status;
    }

    public void setIs_mutual_follow(boolean is_mutual_follow) {
        this.is_mutual_follow = is_mutual_follow;
    }

    public boolean isIs_mutual_follow() {
        return is_mutual_follow;
    }
}