package com.instabond.service.search;

import com.instabond.dto.UserSearchDTO;
import com.instabond.entity.Relationship;
import com.instabond.entity.User;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSearchStrategy implements SearchStrategy {
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public String getType() {
        return "User";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        return search(keyword, pageable, null);
    }

    @Override
    public List<?> suggest(String keyword) {
        return suggest(keyword, null);
    }

    @Override
    public List<?> search(String keyword, Pageable pageable, String callerPrincipal) {
        String safeKeyword = escapeRegex(keyword);
        List<User> users = userRepository.searchUsers(safeKeyword, pageable);
        Set<String> blockedIds = getBlockedUserIds(callerPrincipal);
        return users.stream()
                .filter(user -> !blockedIds.contains(user.getId()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<?> suggest(String keyword, String callerPrincipal) {
        String safeKeyword = escapeRegex(keyword);
        String prefixKeyword = "^" + safeKeyword;

        List<User> users = userRepository.searchUsers(prefixKeyword, PageRequest.of(0, 8));
        Set<String> blockedIds = getBlockedUserIds(callerPrincipal);
        return users.stream()
                .filter(user -> !blockedIds.contains(user.getId()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
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

    private Set<String> getBlockedUserIds(String callerPrincipal) {
        Set<String> blockedIds = new HashSet<>();
        if (callerPrincipal == null || callerPrincipal.isBlank()) {
            return blockedIds;
        }

        User caller = userRepository.findByEmail(callerPrincipal)
                .or(() -> userRepository.findByUsername(callerPrincipal))
                .or(() -> userRepository.findById(callerPrincipal))
                .orElse(null);

        if (caller == null) {
            return blockedIds;
        }

        Query outgoingBlocked = new Query(new Criteria().andOperator(
                Criteria.where("requester_id").is(caller.getId()),
                Criteria.where("status").is("blocked")));
        Query incomingBlocked = new Query(new Criteria().andOperator(
                Criteria.where("recipient_id").is(caller.getId()),
                Criteria.where("status").is("blocked")));

        mongoTemplate.find(outgoingBlocked, Relationship.class).forEach(rel -> blockedIds.add(rel.getRecipient_id()));
        mongoTemplate.find(incomingBlocked, Relationship.class).forEach(rel -> blockedIds.add(rel.getRequester_id()));

        return blockedIds;
    }
}
