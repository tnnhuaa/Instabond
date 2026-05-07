package com.instabond.controller;

import com.instabond.dto.*;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.service.PostService;
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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Create, read, update and delete posts")
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;

    // AI-Suggestion
    @Operation(
            summary = "Get AI suggestions for an image",
            description = "Returns a list of suggested user tags for the given image URL, a list of music suggestions, and other relevant metadata."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI tag suggestions returned successfully",
                    content = @Content(schema = @Schema(implementation = PostSuggestionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid image URL"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "5xx", description = "AI service error")
    })
    @PostMapping(value = "/suggestions", consumes = "multipart/form-data")
    public ResponseEntity<?> getPostSuggestions(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Input image", required = true)
            @RequestPart("image") MultipartFile image) {

        if (image == null || image.isEmpty()) {
            throw new ResourceNotFoundException("Image file is required for analysis.");
        }

        PostSuggestionResponse response = postService.getPostSuggestions(image, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

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
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestPart(value = "request", required = false) CreatePostRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        String callerEmail = getUserId(userDetails);
        return ResponseEntity.status(201).body(postService.createPost(callerEmail, request, files));
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
    public ResponseEntity<List<PostResponse>> getFeed(
            @AuthenticationPrincipal UserDetails userDetails,
                        @Parameter(description = "Feed mode: following or for_you", example = "for_you")
                        @RequestParam(defaultValue = "following") String mode,
                        @Parameter(description = "Stable ordering seed for for_you mode", example = "1713177000000")
                        @RequestParam(required = false) Long seed,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size) {
                                return ResponseEntity.ok(postService.getFeed(getUserId(userDetails), page, size, mode, seed));
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
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getPostById(postId, getUserId(userDetails)));
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
            @PathVariable String userId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getPostsByUserId(userId, getUserId(userDetails), page, size));
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
            @PathVariable String username,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getPostsByUsername(username, getUserId(userDetails), page, size));
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
            @PathVariable String email,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getPostsByEmail(email, getUserId(userDetails), page, size));
    }

    // Get posts where the user is tagged (Photos of You)

    @Operation(
            summary = "Get posts where user is tagged",
            description = "Returns all posts where the given user appears in the tagged_users list, sorted by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of tagged posts",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/tagged/{userId}")
    public ResponseEntity<List<PostResponse>> getTaggedPosts(
            @Parameter(description = "ID of the target user", example = "65b111111111111111111111")
            @PathVariable String userId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getTaggedPostsForUser(userId, getUserId(userDetails), page, size));
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
            @Valid @RequestBody UpdatePostRequest request) {

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

    // Like a post

    @Operation(
            summary = "Like a post",
            description = "Adds a like from the authenticated user to the post. If already liked, this call is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Like processed successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @PostMapping(value = "/{postId}/like", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<PostResponse> likePost(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.likePost(postId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Unlike a post",
            description = "Removes the authenticated user's like from the post. If not liked yet, this call is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unlike processed successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @DeleteMapping("/{postId}/like")
    public ResponseEntity<PostResponse> unlikePost(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.unlikePost(postId, getUserId(userDetails)));
    }

    // Share a post

    @Operation(
            summary = "Share a post",
            description = "Creates a share interaction on a post and increases `stats.shares` by 1. Users can share multiple times."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post shared successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @PostMapping(value = "/{postId}/share", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<PostResponse> sharePost(
            @Parameter(description = "ID of the post to share", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.sharePost(postId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Unshare a post",
            description = "Removes one share interaction of the authenticated user from the post and decreases `stats.shares` by 1. If not shared yet, this call is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unshare processed successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @DeleteMapping("/{postId}/share")
    public ResponseEntity<PostResponse> unsharePost(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.unsharePost(postId, getUserId(userDetails)));
    }

    // Add comment to a post

    @Operation(
            summary = "Add comment to a post",
            description = "Creates a new comment interaction on a post and increases post comment stats."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment created successfully",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.status(201).body(postService.addComment(postId, getUserId(userDetails), request));
    }

    @Operation(
            summary = "Get comments of a post",
            description = "Returns comments of the given post ordered by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comments returned successfully",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        String callerEmail = userDetails != null ? getUserId(userDetails) : null;
        return ResponseEntity.ok(postService.getComments(postId, callerEmail, page, size));
    }

    // Like a comment

    @Operation(
            summary = "Like a comment",
            description = "Adds a like from the authenticated user to the comment."
    )
    @PostMapping(value = "/{postId}/comments/{commentId}/like", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> likeComment(
            @PathVariable String postId,
            @PathVariable String commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.likeComment(postId, commentId, getUserId(userDetails));
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Unlike a comment",
            description = "Removes the authenticated user's like from the comment."
    )
    @DeleteMapping("/{postId}/comments/{commentId}/like")
    public ResponseEntity<Void> unlikeComment(
            @PathVariable String postId,
            @PathVariable String commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.unlikeComment(postId, commentId, getUserId(userDetails));
        return ResponseEntity.ok().build();
    }

    // Delete a comment

    @Operation(
            summary = "Delete a comment",
            description = "Deletes a comment created by the authenticated user and decreases the post comment count."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — caller is not the comment author"),
            @ApiResponse(responseCode = "404", description = "Post or comment not found")
    })
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @Parameter(description = "ID of the comment", example = "65b999999999999999999991")
            @PathVariable String commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.deleteComment(postId, commentId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }
    // Bookmark a post

    @Operation(
            summary = "Bookmark a post",
            description = "Saves a post to the authenticated user's bookmarks. Idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post bookmarked successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @PostMapping(value = "/{postId}/bookmark", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<PostResponse> bookmarkPost(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.bookmarkPost(postId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Remove bookmark from a post",
            description = "Removes a post from the authenticated user's bookmarks. Idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmark removed successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Post not found")
    })
    @DeleteMapping("/{postId}/bookmark")
    public ResponseEntity<PostResponse> unbookmarkPost(
            @Parameter(description = "ID of the target post", example = "65b444444444444444444441")
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.unbookmarkPost(postId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Get bookmarked posts",
            description = "Returns bookmarked posts of the authenticated user, sorted by newest bookmark first. Supports pagination."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmarked posts returned successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/bookmarks")
    public ResponseEntity<List<PostResponse>> getBookmarkedPosts(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(postService.getBookmarkedPosts(getUserId(userDetails), page, size));
    }

    // Returns the email (username) of the currently authenticated user
    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }
}
