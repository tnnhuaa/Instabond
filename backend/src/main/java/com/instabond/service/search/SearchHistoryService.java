package com.instabond.service.search;

import com.instabond.dto.SearchHistoryDTO;
import com.instabond.dto.SearchHistoryRequest;
import com.instabond.entity.SearchHistory;
import com.instabond.enums.SearchType;
import com.instabond.repository.SearchHistoryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    private final SearchHistoryRepository historyRepository;
    private static final int MAX_HISTORY_LIMIT = 15;

    public List<SearchHistoryDTO> getUserSearchHistory(String userId) {
        return historyRepository.getSearchHistoryWithUserDetails(userId, MAX_HISTORY_LIMIT);
    }

    public void saveSearchHistory(String userId, SearchHistoryRequest request) {
        if (request.getType() == null) return;

        Optional<SearchHistory> existing = Optional.empty();

        // Check existing history based on type
        if (request.getType() == SearchType.TEXT && request.getKeyword() != null) {
            String cleanKeyword = request.getKeyword().trim();
            existing = historyRepository.findByUserIdAndTypeAndKeyword(userId, SearchType.TEXT, cleanKeyword);
        }
        else if (request.getType() == SearchType.PROFILE && request.getTargetUserId() != null) {
            existing = historyRepository.findByUserIdAndTypeAndTargetUserId(userId, SearchType.PROFILE, request.getTargetUserId());
        }

        // Logic Upsert
        if (existing.isPresent()) {
            SearchHistory history = existing.get();
            history.setUpdatedAt(LocalDateTime.now());
            historyRepository.save(history);
        } else {
            SearchHistory newHistory = new SearchHistory();
            newHistory.setUserId(userId);
            newHistory.setType(request.getType());

            if (request.getType() == SearchType.TEXT) {
                newHistory.setKeyword(request.getKeyword().trim());
            } else {
                newHistory.setTargetUserId(request.getTargetUserId());
            }

            newHistory.setUpdatedAt(LocalDateTime.now());
            historyRepository.save(newHistory);

            //Limit Queue
            long count = historyRepository.countByUserId(userId);
            if (count > MAX_HISTORY_LIMIT) {
                int excessCount = (int) (count - MAX_HISTORY_LIMIT);
                List<SearchHistory> oldest = historyRepository.findByUserIdOrderByUpdatedAtAsc(
                        userId, PageRequest.of(0, excessCount));
                historyRepository.deleteAll(oldest);
            }
        }
    }

    public void deleteSearchHistory(String userId, String historyId) {
        historyRepository.deleteByIdAndUserId(historyId, userId);
    }
}