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
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PostSearchStrategy implements SearchStrategy {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RelationshipRepository relationshipRepository;

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
        User currentUser = resolveUserFromPrincipal(userEmail);

        List<Post> posts = postRepository.searchPosts(keyword, pageable);

        return posts.stream()
                // I. Media is empty => no display
                .filter(post -> post.getMedia() != null && !post.getMedia().isEmpty())

                // II. Process privacy
                .filter(post -> {
                    if (post.getAuthor_id().equals(currentUser.getId())) {
                        return true;
                    }

                    User author = userRepository.findById(post.getAuthor_id()).orElse(null);
                    if (author == null) return false;

                    boolean isPrivate = author.getSettings() != null
                            && Boolean.TRUE.equals(author.getSettings().getIs_private());

                    // No private
                    if (!isPrivate) {
                        return true;
                    }

                    // Private => check relationship
                    return relationshipRepository.findByRequesterIdAndRecipientId(currentUser.getId(), author.getId())
                            .map(relationship -> "ACCEPTED".equals(relationship.getStatus()))
                            .orElse(false);
                })
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

        List<String> shuffledIds = postRepository.findAllPostIds().stream()
                .map(PostRepository.PostIdProjection::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        if (shuffledIds.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(shuffledIds, new Random(seed));

        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= shuffledIds.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + pageable.getPageSize(), shuffledIds.size());
        List<String> pagedIds = shuffledIds.subList(fromIndex, toIndex);

        // Query posts
        List<Post> posts = postRepository.findByIdIn(pagedIds);

        Map<String, Integer> orderIndex = IntStream.range(0, pagedIds.size())
                .boxed()
                .collect(Collectors.toMap(pagedIds::get, i -> i));

        posts.sort(Comparator.comparingInt(post -> orderIndex.getOrDefault(post.getId(), Integer.MAX_VALUE)));

        return posts.stream()
                // I. Media is empty => no display
                .filter(post -> post.getMedia() != null && !post.getMedia().isEmpty())

                // II. Process privacy
                .filter(post -> {
                    if (post.getAuthor_id().equals(currentUser.getId())) {
                        return true;
                    }

                    User author = userRepository.findById(post.getAuthor_id()).orElse(null);
                    if (author == null) return false;

                    boolean isPrivate = author.getSettings() != null
                            && Boolean.TRUE.equals(author.getSettings().getIs_private());

                    // No private
                    if (!isPrivate) {
                        return true;
                    }

                    // Private => check relationship
                    return relationshipRepository.findByRequesterIdAndRecipientId(currentUser.getId(), author.getId())
                            .map(relationship -> "ACCEPTED".equals(relationship.getStatus()))
                            .orElse(false);
                })
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

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
