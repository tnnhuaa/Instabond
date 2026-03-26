package com.instabond.service.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AudioSearchStrategy implements SearchStrategy {
    // @TODO: Inject AudioRepository when available

    @Override
    public String getType() {
        return "Audio";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        // @TODO: Implement audio search logic using AudioRepository
        // return List<AudioSearchDTO>
        return null;
    }

    @Override
    public List<?> suggest(String keyword) {
        throw new UnsupportedOperationException("Search suggestions are only supported for USER type");
    }
}
