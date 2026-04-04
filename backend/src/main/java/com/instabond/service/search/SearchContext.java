package com.instabond.service.search;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SearchContext {
    private final Map<String, SearchStrategy> strategies;
    private final PostSearchStrategy postSearchStrategy;

    public SearchContext(List<SearchStrategy> strategyList, PostSearchStrategy postSearchStrategy) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        strategy -> strategy.getType().toUpperCase(),
                        Function.identity()
                ));
        this.postSearchStrategy = postSearchStrategy;
    }

    public List<?> executeSearch(String type, String keyword, Pageable pageable) {
        return executeSearch(type, keyword, pageable, null);
    }

    public List<?> executeSearch(String type, String keyword, Pageable pageable, String callerPrincipal) {
        SearchStrategy strategy = strategies.get(type.toUpperCase());

        if (strategy == null) {
            throw new IllegalArgumentException("Invalid search type: " + type);
        }

        return strategy.search(keyword, pageable, callerPrincipal);
    }

    public List<?> executeSuggest(String type, String keyword) {
        return executeSuggest(type, keyword, null);
    }

    public List<?> executeSuggest(String type, String keyword, String callerPrincipal) {
        SearchStrategy strategy = strategies.get(type.toUpperCase());

        if (strategy == null) {
            throw new IllegalArgumentException("Invalid search type: " + type);
        }

        return strategy.suggest(keyword, callerPrincipal);
    }

    public List<?> executeExplore(long seed, Pageable pageable, String callerPrincipal) {
        return postSearchStrategy.explore(seed, pageable, callerPrincipal);
    }
}
