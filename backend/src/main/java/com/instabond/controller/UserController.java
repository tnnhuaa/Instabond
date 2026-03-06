package com.instabond.controller;

import com.instabond.dto.FollowUserResponse;
import com.instabond.dto.ProfileResponse;
import com.instabond.dto.UpdateProfileRequest;
import com.instabond.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "View and update user profiles, followers and following lists")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // My profile

    @Operation(
            summary = "Get current user profile",
            description = "Returns basic info of the authenticated user derived from the JWT token (id, username, email, full_name, avatar_url, bio)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user info returned successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getMe(userDetails.getUsername()));
    }

    // Get all users

    @Operation(
            summary = "Get all users",
            description = "Returns a list of profiles for all users in the system, including posts_count, followers_count and following_count."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of ProfileResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping
    public ResponseEntity<List<ProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    //  Get profile by userId

    @Operation(
            summary = "Get profile by user ID",
            description = """
                    Returns the full profile of a user by their `userId`.
                    
                    **Used for:** Profile Screen — displays user info alongside post count, followers and following.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ProfileResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfileResponse> getProfile(
            @Parameter(description = "MongoDB ObjectId of the user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id) {

        return ResponseEntity.ok(userService.getProfile(id));
    }

    //  Get profile by username

    @Operation(
            summary = "Get profile by username",
            description = "Finds and returns a user's profile by their `username`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ProfileResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Username not found")
    })
    @GetMapping("/username/{username}")
    public ResponseEntity<ProfileResponse> getProfileByUsername(
            @Parameter(description = "Username of the target user", example = "john_doe")
            @PathVariable String username) {

        return ResponseEntity.ok(userService.getProfileByUsername(username));
    }

    // Update profile

    @Operation(
            summary = "Update profile",
            description = """
                    Update the authenticated user's profile information.
                    
                    - Only the **owner** of the profile can update it (`{id}` must match the token).
                    - Fields not included in the request body will **retain their current values**.
                    - Updatable fields: `full_name`, `bio`, `phone_number`, `settings`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully — returns updated ProfileResponse",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot update another user's profile"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProfile(
            @Parameter(description = "ID of the user to update", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {

        String callerId = userService.getMe(userDetails.getUsername()).getId();
        if (!id.equals(callerId)) {
            return ResponseEntity.status(403).body("Forbidden — you cannot update another user's profile.");
        }

        return ResponseEntity.ok(userService.updateProfile(id, request));
    }

    // Update avatar

    @Operation(
            summary = "Update profile avatar",
            description = """
                    Uploads a new avatar to Cloudinary and updates the `avatar_url` in the database.
                    
                    - **Content-Type:** `multipart/form-data`
                    - **`file`** (required): Image file (jpg, png, webp, ...)
                    - Only the **owner** of the profile can change the avatar.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar updated successfully — returns updated ProfileResponse",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid file"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot update another user's avatar"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateAvatar(
            @Parameter(description = "ID of the user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "New avatar image file")
            @RequestPart("file") MultipartFile file) {

        String callerId = userService.getMe(userDetails.getUsername()).getId();
        if (!id.equals(callerId)) {
            return ResponseEntity.status(403).body("Forbidden — you cannot update another user's avatar.");
        }

        return ResponseEntity.ok(userService.updateAvatar(id, file));
    }

    // Followers

    @Operation(
            summary = "Get followers list",
            description = "Returns a list of users who are following `{id}` (relationship status = accepted)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of FollowUserResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(
            @Parameter(description = "ID of the user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id) {

        return ResponseEntity.ok(userService.getFollowers(id));
    }

    // Following

    @Operation(
            summary = "Get following list",
            description = "Returns a list of users that `{id}` is following (relationship status = accepted)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of FollowUserResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}/following")
    public ResponseEntity<List<FollowUserResponse>> getFollowing(
            @Parameter(description = "ID of the user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id) {

        return ResponseEntity.ok(userService.getFollowing(id));
    }
}
