package com.instabond.service.search;

import com.instabond.dto.PostSearchDTO;
import com.instabond.entity.Post;
import com.instabond.entity.User;
import com.instabond.exception.ForbiddenOperationException;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.PostRepository;
import com.instabond.repository.RelationshipRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostSearchStrategy implements SearchStrategy {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RelationshipRepository relationshipRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public String getType() {
        return "Post";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        throw new IllegalArgumentException("User must be logged in to search posts");
    }

    @Override
    public List<?> search(String keyword, Pageable pageable, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new ForbiddenOperationException("User must be logged in to search posts");
        }
        User currentUser = resolveUserFromPrincipal(userEmail);

        // Fetch a pool of posts to allow safe in-memory privacy filtering and pagination.
        Query query = new Query();

        // Search by keyword in caption or location
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("caption").regex(keyword, "i"),
                Criteria.where("location.name").regex(keyword, "i")
        ));

        // Must contain media
        query.addCriteria(Criteria.where("media").exists(true).not().size(0));

        if (pageable.getSort().isEmpty()) {
            query.with(pageable.getSort());
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));
        }

        query.limit(500);

        List<Post> poolPosts = mongoTemplate.find(query, Post.class);

        if (poolPosts.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch load Authors and Relationships

        Set<String> authorIds = poolPosts.stream()
                .map(Post::getAuthor_id)
                .collect(Collectors.toSet());

        Map<String, User> authorsMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<String> acceptedFollowingIds = getAcceptedFollowingIds(currentUser.getId());

        // In-memory privacy filtering & In-memory pagination

        List<Post> visiblePosts = poolPosts.stream()
                .filter(post -> isPostVisibleToUser(post, currentUser, authorsMap, acceptedFollowingIds))
                .collect(Collectors.toList());

        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= visiblePosts.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(fromIndex + pageable.getPageSize(), visiblePosts.size());

        // Map to DTO

        return visiblePosts.subList(fromIndex, toIndex).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<?> suggest(String keyword) {
        throw new UnsupportedOperationException("Search suggestions are only supported for USER type");
    }

    public List<PostSearchDTO> explore(long seed, Pageable pageable, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new ForbiddenOperationException("User must be logged in to explore posts");
        }
        User currentUser = resolveUserFromPrincipal(userEmail);

        // Fetch a limited pool of posts that have media
        Query query = new Query(Criteria.where("media").exists(true).not().size(0))
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .limit(500);

        List<Post> poolPosts = mongoTemplate.find(query, Post.class);

        if (poolPosts.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch load Authors to prevent N+1 queries
        Set<String> authorIds = poolPosts.stream()
                .map(Post::getAuthor_id)
                .collect(Collectors.toSet());

        Map<String, User> authorsMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch load Relationships for privacy checks
        Set<String> acceptedFollowingIds = getAcceptedFollowingIds(currentUser.getId());

        // In-memory privacy filtering
        List<Post> visiblePosts = poolPosts.stream()
                .filter(post -> isPostVisibleToUser(post, currentUser, authorsMap, acceptedFollowingIds))
                .collect(Collectors.toList());

        // Deterministic shuffle for stable pagination
        Collections.shuffle(visiblePosts, new Random(seed));

        // Paginate the filtered results securely
        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= visiblePosts.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(fromIndex + pageable.getPageSize(), visiblePosts.size());

        return visiblePosts.subList(fromIndex, toIndex).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // HELPER METHODS

    private PostSearchDTO mapToDTO(Post post) {
        String thumbnailUrl = null;
        if (post.getMedia() != null && !post.getMedia().isEmpty()) {
            thumbnailUrl = post.getMedia().get(0).getUrl();
        }

        int likes = post.getStats() != null ? post.getStats().getLikes() : 0;
        int comments = post.getStats() != null ? post.getStats().getComments() : 0;

        return PostSearchDTO.builder()
                .id(post.getId())
                .author_id(post.getAuthor_id())
                .caption(post.getCaption())
                .thumbnail_url(thumbnailUrl)
                .likes(likes)
                .comments(comments)
                .build();
    }

    private boolean isPostVisibleToUser(Post post, User currentUser, Map<String, User> authorsMap, Set<String> acceptedFollowingIds) {
        String authorId = post.getAuthor_id();

        if (authorId.equals(currentUser.getId())) {
            return true;
        }

        User author = authorsMap.get(authorId);
        if (author == null) {
            return false;
        }

        boolean isPrivate = author.getSettings() != null && Boolean.TRUE.equals(author.getSettings().getIs_private());

        if (!isPrivate) {
            return true;
        }

        return acceptedFollowingIds.contains(authorId);
    }

    private Set<String> getAcceptedFollowingIds(String userId) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("requester_id").is(userId),
                Criteria.where("status").is("ACCEPTED")
        ));

        return mongoTemplate.find(query, Map.class, "relationships").stream()
                .map(map -> map.get("recipient_id"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    // Mapper

    private User resolveUserFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Invalid user principal");
        }
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .or(() -> userRepository.findById(principal))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal));
    }
}
