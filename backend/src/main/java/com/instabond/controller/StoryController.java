package com.instabond.controller;

import com.instabond.dto.StoryResponse;
import com.instabond.dto.StoryViewersResponse;
import com.instabond.service.StoryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Stories", description = "Create and read active stories")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService storyService;

    @Operation(
            summary = "Create a new image story",
            description = "Uploads an image and creates a story that expires automatically 24 hours after creation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Story created successfully",
                    content = @Content(schema = @Schema(implementation = StoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or request data"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryResponse> createImageStory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Image file to upload", required = true)
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.status(201).body(storyService.createImageStory(getUserId(userDetails), file));
    }

    @Operation(
            summary = "Get active story feed",
            description = "Returns active stories of the authenticated user and accepted followings, sorted by newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active story feed returned successfully",
                    content = @Content(schema = @Schema(implementation = StoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping("/feed")
    public ResponseEntity<List<StoryResponse>> getActiveFeed(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(storyService.getActiveFeed(getUserId(userDetails)));
    }

    @Operation(
            summary = "Mark a story as viewed",
            description = "Creates or updates the viewer entry for the authenticated user on the given active story."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Story view tracked successfully",
                    content = @Content(schema = @Schema(implementation = StoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    @PostMapping("/{storyId}/view")
    public ResponseEntity<StoryResponse> markStoryViewed(
            @PathVariable String storyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(storyService.markStoryViewed(storyId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Heart a story",
            description = "Marks the active story as liked by the authenticated user and ensures they are stored as a viewer."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Story liked successfully",
                    content = @Content(schema = @Schema(implementation = StoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "The authenticated user cannot like their own story"),
            @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    @PostMapping("/{storyId}/like")
    public ResponseEntity<StoryResponse> likeStory(
            @PathVariable String storyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(storyService.setStoryLiked(storyId, getUserId(userDetails), true));
    }

    @Operation(
            summary = "Remove story heart",
            description = "Removes the authenticated user's heart reaction from the active story while keeping the viewer record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Story like removed successfully",
                    content = @Content(schema = @Schema(implementation = StoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    @DeleteMapping("/{storyId}/like")
    public ResponseEntity<StoryResponse> unlikeStory(
            @PathVariable String storyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(storyService.setStoryLiked(storyId, getUserId(userDetails), false));
    }

    @Operation(
            summary = "Get viewers for one story",
            description = "Returns the viewers of an active story, including which viewers hearted it. Only the story author may access this."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Story viewers returned successfully",
                    content = @Content(schema = @Schema(implementation = StoryViewersResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "The authenticated user is not the story author"),
            @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    @GetMapping("/{storyId}/viewers")
    public ResponseEntity<StoryViewersResponse> getStoryViewers(
            @PathVariable String storyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(storyService.getStoryViewers(storyId, getUserId(userDetails)));
    }

    @Operation(
            summary = "Delete a story",
            description = "Deletes one story owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Story deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "The authenticated user is not the story author"),
            @ApiResponse(responseCode = "404", description = "Story not found")
    })
    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> deleteStory(
            @PathVariable String storyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        storyService.deleteStory(storyId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }
}
