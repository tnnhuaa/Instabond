package com.instabond.controller;

import com.instabond.dto.FollowUserResponse;
import com.instabond.dto.ProfileResponse;
import com.instabond.dto.ProfileShareResponse;
import com.instabond.dto.ResolveProfileRequest;
import com.instabond.dto.ChangePasswordRequest;
import com.instabond.dto.UpdateAllowTaggingResponse;
import com.instabond.dto.UpdatePrivacyRequest;
import com.instabond.dto.UpdateProfileRequest;
import com.instabond.exception.ForbiddenOperationException;
import com.instabond.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    // Face Detection

    @Operation(
            summary = "Register face embedding",
            description = "Uploads face images for the authenticated user, generates a face embedding, and stores it in the database for future face recognition.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Face embedding registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing image file"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping(value = "/register-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registerFace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Array of exactly 3 image files containing the user's face", required = true)
            @RequestParam("images") List<MultipartFile> images) {

        userService.registerFace(userDetails.getUsername(), images);
        return ResponseEntity.ok().build();
    }

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

    // Update privacy setting

    @Operation(
            summary = "Update my privacy setting",
            description = "Sets the authenticated account to private or public by `is_private`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Privacy updated successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PatchMapping("/me/privacy")
    public ResponseEntity<ProfileResponse> updateMyPrivacy(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePrivacyRequest request) {

        return ResponseEntity.ok(userService.updateMyPrivacy(userDetails.getUsername(), request.getIs_private()));
    }

    @Operation(
            summary = "Enable private account",
            description = "Sets the authenticated account to private mode (`is_private = true`)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private mode enabled",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PostMapping("/me/private")
    public ResponseEntity<ProfileResponse> enablePrivateMode(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.updateMyPrivacy(userDetails.getUsername(), true));
    }

    @Operation(
            summary = "Disable private account",
            description = "Sets the authenticated account to public mode (`is_private = false`)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private mode disabled",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @DeleteMapping("/me/private")
    public ResponseEntity<ProfileResponse> disablePrivateMode(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.updateMyPrivacy(userDetails.getUsername(), false));
    }

    @Operation(
            summary = "Change account password",
            description = "Changes password for the authenticated account after validating current password and confirmation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password payload or validation failure"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<String> changeMyPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changeMyPassword(userDetails.getUsername(), request);
        return ResponseEntity.ok("Password changed successfully.");
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
    public ResponseEntity<List<ProfileResponse>> getAllUsers(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(userService.getAllUsers(page, limit));
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
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getProfile(id, userDetails.getUsername()));
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
            @PathVariable String username,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getProfileByUsername(username, userDetails.getUsername()));
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
            @Valid @RequestBody UpdateProfileRequest request) {

        String callerId = userService.getMe(userDetails.getUsername()).getId();
        if (!id.equals(callerId)) {
            throw new ForbiddenOperationException("Forbidden - you cannot update another user's profile.");
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
            throw new ForbiddenOperationException("Forbidden - you cannot update another user's avatar.");
        }

        return ResponseEntity.ok(userService.updateAvatar(id, file));
    }

    // Device token
    @PostMapping("/device-token")
    public ResponseEntity<Void> addDeviceToken(
            @RequestParam String token,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.addDeviceToken(userDetails.getUsername(), token);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<Void> removeDeviceToken(
            @RequestParam String token,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.removeDeviceToken(userDetails.getUsername(), token);
        return ResponseEntity.ok().build();
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
            @PathVariable String id,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getFollowers(id, userDetails.getUsername(), page, limit));
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
            @PathVariable String id,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getFollowing(id, userDetails.getUsername(), page, limit));
    }

    // Friends (Mutual Followers)

    @Operation(
            summary = "Get friends list",
            description = "Returns a list of mutual followers (users that follow {id} and are also followed by {id})."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of FollowUserResponse returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}/friends")
    public ResponseEntity<List<FollowUserResponse>> getFriends(
            @Parameter(description = "ID of the user", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getFriends(id, userDetails.getUsername(), page, limit));
    }

    // Follow/Unfollow

    @Operation(
            summary = "Follow a user",
            description = "Creates or re-activates a follow relationship from the authenticated user to `{id}`. For private accounts, response status becomes `pending` until accepted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Follow action processed (accepted or pending)",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot follow yourself"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping(value = "/{id}/follow", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<FollowUserResponse> followUser(
            @Parameter(description = "ID of user to follow", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.followUser(id, userDetails.getUsername()));
    }

    @Operation(
            summary = "Unfollow a user",
            description = "Deletes follow relationship from authenticated user to `{id}`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Unfollowed successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot unfollow yourself"),
            @ApiResponse(responseCode = "404", description = "User or relationship not found")
    })
    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollowUser(
            @Parameter(description = "ID of user to unfollow", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.unfollowUser(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove a follower",
            description = "Removes follow relationship from `{id}` to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Follower removed successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot remove yourself"),
            @ApiResponse(responseCode = "404", description = "User or relationship not found")
    })
    @DeleteMapping("/{id}/follower")
    public ResponseEntity<Void> removeFollower(
            @Parameter(description = "ID of follower to remove", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.removeFollower(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    // Close friends

    @Operation(
            summary = "Add close friend",
            description = "Marks a followed user as close friend for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated close-friend status successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User or relationship not found")
    })
    @PostMapping("/{id}/close-friend")
    public ResponseEntity<FollowUserResponse> addCloseFriend(
            @Parameter(description = "ID of target user", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.setCloseFriend(id, userDetails.getUsername(), true));
    }

    @Operation(
            summary = "Remove close friend",
            description = "Resets a close-friend relationship back to normal follow."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated close-friend status successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User or relationship not found")
    })
    @DeleteMapping("/{id}/close-friend")
    public ResponseEntity<FollowUserResponse> removeCloseFriend(
            @Parameter(description = "ID of target user", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.setCloseFriend(id, userDetails.getUsername(), false));
    }

    @Operation(
            summary = "Get close friends list",
            description = "Returns users marked as close friend by `{id}`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}/close-friends")
    public ResponseEntity<List<FollowUserResponse>> getCloseFriends(
            @Parameter(description = "ID of the user", example = "65b111111111111111111111")
            @PathVariable String id,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getCloseFriends(id, userDetails.getUsername(), page, limit));
    }

    // Follow requests (private accounts)

    @Operation(
            summary = "Get incoming follow requests",
            description = "Returns pending follow requests for the authenticated user (mainly for private accounts)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending requests returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/follow-requests/incoming")
    public ResponseEntity<List<FollowUserResponse>> getIncomingFollowRequests(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getIncomingFollowRequests(userDetails.getUsername(), page, limit));
    }

    @Operation(
            summary = "Accept follow request",
            description = "Accepts a pending request from `{requesterId}` to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Request or user not found")
    })
    @PostMapping("/follow-requests/{requesterId}/accept")
    public ResponseEntity<FollowUserResponse> acceptFollowRequest(
            @Parameter(description = "Requester user ID", example = "65b222222222222222222222")
            @PathVariable String requesterId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.acceptFollowRequest(requesterId, userDetails.getUsername()));
    }

    @Operation(
            summary = "Reject follow request",
            description = "Rejects a pending request from `{requesterId}` to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Request rejected successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Request or user not found")
    })
    @PostMapping("/follow-requests/{requesterId}/reject")
    public ResponseEntity<Void> rejectFollowRequest(
            @Parameter(description = "Requester user ID", example = "65b222222222222222222222")
            @PathVariable String requesterId,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.rejectFollowRequest(requesterId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get sent follow requests",
            description = "Returns pending follow requests that the authenticated user has sent to others."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sent requests returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/follow-requests/sent")
    public ResponseEntity<List<FollowUserResponse>> getSentFollowRequests(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getSentFollowRequests(userDetails.getUsername(), page, limit));
    }

    @Operation(
            summary = "Cancel sent follow request",
            description = "Cancels a pending follow request that the authenticated user sent to `{recipientId}`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sent request canceled successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Pending request or user not found")
    })
    @DeleteMapping("/follow-requests/{recipientId}/cancel")
    public ResponseEntity<Void> cancelSentFollowRequest(
            @Parameter(description = "Recipient user ID", example = "65b222222222222222222222")
            @PathVariable String recipientId,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.cancelSentFollowRequest(recipientId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    // Block User

    @Operation(
            summary = "Block a user",
            description = "Blocks the target user. Automatically removes any follow relationships in both directions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User blocked successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — cannot block yourself"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping(value = "/{id}/block", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> blockUser(
            @Parameter(description = "ID of user to block", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.blockUser(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Unblock a user",
            description = "Removes the block on the target user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User unblocked successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User or block not found")
    })
    @DeleteMapping("/{id}/block")
    public ResponseEntity<Void> unblockUser(
            @Parameter(description = "ID of user to unblock", example = "65b222222222222222222222")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.unblockUser(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get blocked users list",
            description = "Returns the list of users that the authenticated user has blocked."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Blocked users returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/blocked")
    public ResponseEntity<List<FollowUserResponse>> getBlockedUsers(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getBlockedUsers(userDetails.getUsername(), page, limit));
    }

    // Friend Suggestions

    @Operation(
            summary = "Get friend suggestions",
            description = "Returns suggested users based on friends-of-friends algorithm, excluding already-followed and blocked users."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggestions returned successfully",
                    content = @Content(schema = @Schema(implementation = FollowUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/suggestions")
    public ResponseEntity<List<FollowUserResponse>> getFriendSuggestions(
            @Parameter(description = "Maximum number of suggestions", example = "10")
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getFriendSuggestions(userDetails.getUsername(), limit));
    }

    // QR Profile Resolve

    @Operation(
            summary = "Resolve profile from QR code",
            description = "Resolves a user profile from a userId (typically scanned from QR code). Returns full profile with relationship status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile resolved successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/resolve")
    public ResponseEntity<ProfileResponse> resolveProfile(
            @Parameter(description = "Target user ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Target username", example = "john_doe")
            @RequestParam(required = false) String username,
            @Parameter(description = "Target QR UID", example = "qr_65b111111111111111111111")
            @RequestParam(required = false) String qrUid,
            @Parameter(description = "Raw payload from deep-link/share text", example = "instabond://profile?uid=qr_65b111111111111111111111")
            @RequestParam(required = false) String payload,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.resolveProfileQuery(userId, username, qrUid, payload, userDetails.getUsername()));
    }

    @Operation(
            summary = "Resolve profile from payload",
            description = "Resolves a profile from raw QR/deep-link/share payload to unify scan and share-open flows."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile resolved successfully",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/resolve")
    public ResponseEntity<ProfileResponse> resolveProfileByPayload(
            @Valid @RequestBody ResolveProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.resolveProfilePayload(request.getPayload(), userDetails.getUsername()));
    }

    @Operation(
            summary = "Get my profile share payload",
            description = "Returns QR UID, deep-link and share text for the authenticated user profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Share payload returned successfully",
                    content = @Content(schema = @Schema(implementation = ProfileShareResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/me/share-profile")
    public ResponseEntity<ProfileShareResponse> getMyShareProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getMyShareProfile(userDetails.getUsername()));
    }

    @Operation(
            summary = "Get profile share payload by user ID",
            description = "Returns QR UID, deep-link and share text for the target profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Share payload returned successfully",
                    content = @Content(schema = @Schema(implementation = ProfileShareResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}/share-profile")
    public ResponseEntity<ProfileShareResponse> getShareProfile(
            @Parameter(description = "ID of the user", example = "65b111111111111111111111")
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(userService.getShareProfile(id, userDetails.getUsername()));
    }

    // Update allow_tagging setting

    @Operation(
            summary = "Update my allow_tagging setting",
            description = "Updates only `settings.allow_tagging` for the authenticated user via query param `value` (`everyone` or `none`)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "allow_tagging updated successfully",
                    content = @Content(schema = @Schema(implementation = UpdateAllowTaggingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid value (must be everyone or none)"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PatchMapping("/me/allow-tagging")
    public ResponseEntity<UpdateAllowTaggingResponse> updateMyAllowTagging(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Allowed values: everyone or none", example = "everyone")
            @RequestParam String value) {

        return ResponseEntity.ok(userService.updateMyAllowTagging(userDetails.getUsername(), value));
    }
}
