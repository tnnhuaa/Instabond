package com.instabond.service.search;

import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * SearchStrategy interface defines the contract for different search strategies.
 * Each implementation will handle a specific type of search (e.g., User, Post, Audio).
 */
public interface SearchStrategy {
    String getType();   // User, Post, Audio, ...

    List<?> search(String keyword, Pageable pageable);

    List<?> suggest(String keyword);

    default List<?> search(String keyword, Pageable pageable, String callerPrincipal) {
        return search(keyword, pageable);
    }

    default List<?> suggest(String keyword, String callerPrincipal) {
        return suggest(keyword);
    }
}
