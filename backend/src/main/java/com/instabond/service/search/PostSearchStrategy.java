package com.instabond.service.search;

import com.instabond.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostSearchStrategy implements SearchStrategy {
    private final PostRepository postRepository;

    @Override
    public String getType() {
        return "Post";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        // @TODO: Implement post search logic using postRepository
        // return List<PostSearchDTO>
        return null;
    }

    @Override
    public List<?> suggest(String keyword) {
        throw new UnsupportedOperationException("Search suggestions are only supported for USER type");
    }
}
