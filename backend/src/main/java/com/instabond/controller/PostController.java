package com.instabond.controller;

import com.instabond.dto.CreatePostRequest;
import com.instabond.dto.PostResponse;
import com.instabond.dto.UpdatePostRequest;
import com.instabond.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Create, read, update and delete posts")
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;

    // Create post

    @Operation(
            summary = "Create a new post",
            description = """
                    Create a post using a JSON body. Include media URLs directly in the `media` array.

                    Send `Authorization: Bearer <token>` header.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Post created successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or expired token")
    })
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreatePostRequest request) {

        String callerEmail = getUserId(userDetails);
        return ResponseEntity.status(201).body(postService.createPost(callerEmail, request, null));
    }

    // Get feed

    @Operation(
            summary = "Get post feed",
            description = "Returns all posts sorted by newest first, used for the Home Feed screen."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of posts returned successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed() {
        return ResponseEntity.ok(postService.getFeed());
    }

    // Get a single post by its ID

    @Operation(
            summary = "Get post by ID",
            description = "Returns full details of a single post by its postId."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post returned successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @Parameter(description = "Post ID", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String postId) {

        return ResponseEntity.ok(postService.getPostById(postId));
    }

    // Get all posts authored by a specific user (by userId)

    @Operation(
            summary = "Get all posts by userId",
            description = "Returns all posts authored by the given userId, sorted by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the user's posts",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getPostsByUserId(
            @Parameter(description = "ID of the target user", example = "65b111111111111111111111")
            @PathVariable String userId) {

        return ResponseEntity.ok(postService.getPostsByUserId(userId));
    }

    // Get all posts authored by a specific user (by username)

    @Operation(
            summary = "Get all posts by username",
            description = "Returns all posts authored by the given username, sorted by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the user's posts",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/user/by-username/{username}")
    public ResponseEntity<List<PostResponse>> getPostsByUsername(
            @Parameter(description = "Username of the target user", example = "nam_nguyen")
            @PathVariable String username) {

        return ResponseEntity.ok(postService.getPostsByUsername(username));
    }

    // Get all posts authored by a specific user (by email)

    @Operation(
            summary = "Get all posts by email",
            description = "Returns all posts authored by the given email, sorted by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the user's posts",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/user/by-email/{email}")
    public ResponseEntity<List<PostResponse>> getPostsByEmail(
            @Parameter(description = "Email of the target user", example = "nam@example.com")
            @PathVariable String email) {

        return ResponseEntity.ok(postService.getPostsByEmail(email));
    }

    // Update a post

    @Operation(
            summary = "Update a post",
            description = """
                    Only the author of the post is allowed to update it.

                    Fields that can be updated: caption, location, tagged_users.
                    Fields omitted from the request body will retain their current values.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post updated successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — caller is not the post author"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @Parameter(description = "ID of the post to update", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdatePostRequest request) {

        String userId = getUserId(userDetails);
        return ResponseEntity.ok(postService.updatePost(postId, userId, request));
    }

    // Delete a post

    @Operation(
            summary = "Delete a post",
            description = "Only the author of the post is allowed to delete it. Returns HTTP 204 on success."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — caller is not the post author"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "ID of the post to delete", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.deletePost(postId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    // Returns the email (username) of the currently authenticated user
    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }

    // Global exception handler
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.startsWith("Forbidden")) {
            return ResponseEntity.status(403).body(Map.of("error", msg));
        }
        if (msg != null && (msg.contains("not found") || msg.contains("Not found"))) {
            return ResponseEntity.status(404).body(Map.of("error", msg));
        }
        return ResponseEntity.status(400).body(Map.of("error", msg != null ? msg : "Bad request"));
    }
}

