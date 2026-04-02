package com.instabond.controller.search;

import com.instabond.service.search.SearchContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "REST APIs for searching users, posts, and getting search suggestions")
public class SearchController {

    private final SearchContext searchContext;

    @GetMapping("/results")
    @Operation(
            summary = "Get search results",
            description = "Returns a paginated list of results based on the search type (User, Post, Audio). " +
                    "Use this for the main search result tabs."
    )
    public ResponseEntity<?> getSearchResults(
            @Parameter(description = "Type of content to search for", example = "User")
            @RequestParam String type,

            @Parameter(description = "Search keyword", example = "penaldo")
            @RequestParam String q,

            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page")
                        @RequestParam(defaultValue = "20") int size,

                        @AuthenticationPrincipal UserDetails userDetails) {

        Pageable pageable = PageRequest.of(page, size);
                return ResponseEntity.ok(searchContext.executeSearch(type, q, pageable, userDetails != null ? userDetails.getUsername() : null));
    }

    @GetMapping("/suggestions")
    @Operation(
            summary = "Get search suggestions",
            description = "Returns a quick list of suggestions as the user types. Currently optimized for User search."
    )
    public ResponseEntity<?> getSearchSuggestions(
            @Parameter(description = "Type of suggestion", example = "USER")
            @RequestParam(defaultValue = "USER") String type,

            @Parameter(description = "Current input string in the search bar", example = "pen")
                        @RequestParam String q,

                        @AuthenticationPrincipal UserDetails userDetails) {

                return ResponseEntity.ok(searchContext.executeSuggest(type, q, userDetails != null ? userDetails.getUsername() : null));
    }

    @GetMapping("/explore")
    @Operation(
            summary = "Get explore posts",
            description = "Returns a randomized, paginated list of posts for the explore feed. Uses a seed to maintain consistent pagination without duplicates."
    )
    public ResponseEntity<?> getExplorePosts(
            @Parameter(description = "Seed for randomizing the feed consistently", example = "1711234567890")
            @RequestParam(defaultValue = "0") long seed,

            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(searchContext.executeExplore(seed, pageable));
    }
}