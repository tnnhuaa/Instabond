package com.instabond.service;

import com.instabond.dto.StoryResponse;
import com.instabond.entity.Story;
import com.instabond.entity.User;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.StoryRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Duration STORY_TTL = Duration.ofHours(24);

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final MongoTemplate mongoTemplate;

    public StoryResponse createImageStory(String callerPrincipal, MultipartFile file) {
        User author = resolveUserFromPrincipal(callerPrincipal);
        Instant createdAt = Instant.now();

        Story story = Story.builder()
                .author_id(author.getId())
                .media_url(fileService.uploadImageUrl(file))
                .type("image")
                .viewers(List.of())
                .created_at(createdAt)
                .expires_at(createdAt.plus(STORY_TTL))
                .build();

        return toStoryResponse(storyRepository.save(story), author, 0);
    }

    public List<StoryResponse> getActiveFeed(String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        List<String> authorIds = getAllowedAuthorIds(caller.getId());

        Query storyQuery = new Query(new Criteria().andOperator(
                Criteria.where("author_id").in(buildAuthorIdVariants(authorIds)),
                Criteria.where("expires_at").gt(Instant.now())))
                .with(Sort.by(Sort.Direction.DESC, "created_at"));

        List<Story> stories = mongoTemplate.find(storyQuery, Story.class);
        Map<String, User> authorsById = loadAuthorsById(stories);

        // Batch Intimacy Scores
        Set<String> uniqueAuthorIds = stories.stream()
                .map(Story::getAuthor_id)
                .map(this::normalizeId)
                .collect(Collectors.toSet());
        Map<String, Integer> scoreMap = getIntimacyScoresBatch(caller.getId(), uniqueAuthorIds);

        return stories.stream()
                .map(story -> {
                    String aId = normalizeId(story.getAuthor_id());
                    User author = authorsById.get(aId);
                    int score = scoreMap.getOrDefault(aId, 0);
                    return toStoryResponse(story, author, score);
                })
                .toList();
    }

    private User resolveUserFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Invalid user principal");
        }

        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .or(() -> userRepository.findById(principal))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal));
    }

    private List<String> getAllowedAuthorIds(String callerId) {
        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", callerId),
                Criteria.where("status").is("accepted")));

        List<String> authorIds = new ArrayList<>();
        authorIds.add(callerId);

        List<Map> relationships = mongoTemplate.find(followingQuery, Map.class, "relationships");
        for (Map relationship : relationships) {
            Object recipientId = relationship.get("recipient_id");
            if (recipientId != null) {
                authorIds.add(recipientId.toString());
            }
        }

        return authorIds.stream().distinct().toList();
    }

    private List<Object> buildAuthorIdVariants(List<String> authorIds) {
        List<Object> values = new ArrayList<>();
        for (String authorId : authorIds) {
            values.add(authorId);
            try {
                values.add(new ObjectId(authorId));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private Map<String, User> loadAuthorsById(List<Story> stories) {
        Set<String> authorIds = stories.stream()
                .map(Story::getAuthor_id)
                .map(this::normalizeId)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> authorsById = new LinkedHashMap<>();
        if (!authorIds.isEmpty()) {
            userRepository.findAllById(authorIds).forEach(user -> authorsById.put(user.getId(), user));
        }

        return authorsById;
    }

    private StoryResponse toStoryResponse(Story story, User author, int intimacyScore) {
        StoryResponse.AuthorInfo authorInfo = null;
        if (author != null) {
            authorInfo = StoryResponse.AuthorInfo.builder()
                    .id(author.getId())
                    .username(author.getUsername())
                    .full_name(author.getFull_name())
                    .avatar_url(author.getAvatar_url())
                    .intimacy_score(intimacyScore)
                    .build();
        }

        return StoryResponse.builder()
                .id(story.getId())
                .author(authorInfo)
                .media_url(story.getMedia_url())
                .type(story.getType())
                .created_at(story.getCreated_at())
                .expires_at(story.getExpires_at())
                .build();
    }

    private Criteria idCriteria(String field, String id) {
        List<Criteria> items = new ArrayList<>();
        items.add(Criteria.where(field).is(id));
        try {
            items.add(Criteria.where(field).is(new ObjectId(id)));
        } catch (Exception ignored) {
        }
        return new Criteria().orOperator(items.toArray(new Criteria[0]));
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim();
    }

    // BATCH QUERY HELPER
    private Map<String, Integer> getIntimacyScoresBatch(String callerId, Set<String> authorIds) {
        Map<String, Integer> scoreMap = new java.util.HashMap<>();
        if (callerId == null || authorIds == null || authorIds.isEmpty()) return scoreMap;

        Set<String> targetIds = authorIds.stream()
                .filter(id -> id != null && !id.equals(callerId))
                .collect(Collectors.toSet());

        if (targetIds.isEmpty()) return scoreMap;

        Criteria c1 = new Criteria().andOperator(
                idCriteria("requester_id", callerId),
                Criteria.where("recipient_id").in(targetIds)
        );
        Criteria c2 = new Criteria().andOperator(
                Criteria.where("requester_id").in(targetIds),
                idCriteria("recipient_id", callerId)
        );

        Query relQuery = new Query(new Criteria().orOperator(c1, c2));
        List<Map> rels = mongoTemplate.find(relQuery, Map.class, "relationships");

        for (Map rel : rels) {
            String reqId = rel.get("requester_id").toString();
            String recId = rel.get("recipient_id").toString();
            String otherId = reqId.equals(callerId) ? recId : reqId;

            if (rel.get("intimacy_score") != null) {
                scoreMap.put(otherId, ((Number) rel.get("intimacy_score")).intValue());
            }
        }
        return scoreMap;
    }
}
