package com.instabond.service.search;

import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSearchStrategy implements SearchStrategy {
    private final UserRepository userRepository;

    @Override
    public String getType() {
        return "User";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        // @TODO: Implement user search logic using userRepository
        // return List<UserSearchDTO>
        return null;
    }

    @Override
    public List<?> suggest(String keyword) {
        // 5-10 user match keyword (prefix matching)
        // @TODO: return List<UserSearchDTO>
        return null;
    }
}
