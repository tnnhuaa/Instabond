package com.instabond.service.search;

import com.instabond.dto.UserSearchDTO;
import com.instabond.entity.User;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

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
        String safeKeyword = escapeRegex(keyword);
        List<User> users = userRepository.searchUsers(safeKeyword, pageable);
        return users.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<?> suggest(String keyword) {
        String safeKeyword = escapeRegex(keyword);
        String prefixKeyword = "^" + safeKeyword;   // prefix matching

        List<User> users = userRepository.searchUsers(prefixKeyword, PageRequest.of(0, 8));
        return users.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // === HELPERS ===
    private String escapeRegex(String keyword) {
        if (keyword == null) return "";
        return keyword.replaceAll("([.*+?^=!:${}()|\\[\\]\\/\\\\])", "\\\\$1");
    }

    private UserSearchDTO mapToDTO(User user) {
        Boolean isPrivate = false;
        if (user.getSettings() != null && user.getSettings().getIs_private() != null) {
            isPrivate = user.getSettings().getIs_private();
        }

        return UserSearchDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .is_private(isPrivate)
                .build();
    }
}
