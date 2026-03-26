package com.instabond.controller;

import com.instabond.service.search.SearchContext;
import com.instabond.service.search.SearchStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchContext searchContext;

    @GetMapping("/results")
    public ResponseEntity<?> getSearchResults(
            @RequestParam String type, // "User", "Post", "Audio"
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(searchContext.executeSearch(type, q, pageable));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<?> getSearchSuggestions(
            @RequestParam(defaultValue = "USER") String type,
            @RequestParam String q) {
        return ResponseEntity.ok(searchContext.executeSuggest(type, q));
    }
}
