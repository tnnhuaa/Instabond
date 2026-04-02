package com.instabond.controller.search;

import com.instabond.dto.SearchHistoryDTO;
import com.instabond.dto.SearchHistoryRequest;
import com.instabond.service.search.SearchHistoryService;
import com.instabond.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@RestController
@RequestMapping("/api/v1/search/history")
@RequiredArgsConstructor
@Tag(name = "Search History", description = "Manage user search history (Keywords and Profiles)")
@SecurityRequirement(name = "bearerAuth")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;
    private final UserService userService;

    @Operation(
            summary = "Get search history",
            description = "Returns a list of recent search histories (max 15) for the authenticated user, including user details for PROFILE type searches."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search history list returned successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @GetMapping
    public ResponseEntity<List<SearchHistoryDTO>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userService.getMe(userDetails.getUsername()).getId();

        return ResponseEntity.ok(searchHistoryService.getUserSearchHistory(userId));
    }

    @Operation(
            summary = "Save or update search history",
            description = "Saves a new search history item or updates the timestamp if it already exists (upsert) to push it to the top."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search history saved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @PostMapping
    public ResponseEntity<Void> saveHistory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search history payload. Choose a type from the dropdown to see the specific format.",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Text Search Example",
                                            summary = "When a user searches by keyword",
                                            value = "{ \"type\": \"TEXT\", \"keyword\": \"penaldo\" }"
                                    ),
                                    @ExampleObject(
                                            name = "Profile Visit Example",
                                            summary = "When a user clicks on a profile",
                                            value = "{ \"type\": \"PROFILE\", \"targetUserId\": \"67fb1f1b9d5a6f25a1c6d3b0\" }"
                                    )
                            }
                    )
            )
            @RequestBody SearchHistoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userService.getMe(userDetails.getUsername()).getId();
        searchHistoryService.saveSearchHistory(userId, request);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete a search history item",
            description = "Removes a specific search history record by its ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search history deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistoryItem(
            @Parameter(description = "MongoDB ObjectId of the search history item", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable("id") String historyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userService.getMe(userDetails.getUsername()).getId();
        searchHistoryService.deleteSearchHistory(userId, historyId);

        return ResponseEntity.ok().build();
    }
}