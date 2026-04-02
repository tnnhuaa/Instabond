package com.instabond.service;

import com.instabond.dto.StoryResponse;
import com.instabond.dto.StoryViewerResponse;
import com.instabond.dto.StoryViewersResponse;
import com.instabond.entity.Story;
import com.instabond.entity.User;
import com.instabond.exception.ForbiddenOperationException;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Duration STORY_TTL = Duration.ofHours(24);
    private static final String STORY_HEART_REACTION = "heart";

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

        return toStoryResponse(storyRepository.save(story), author, author.getId());
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

        return stories.stream()
                .map(story -> toStoryResponse(story, authorsById.get(normalizeId(story.getAuthor_id())), caller.getId()))
                .toList();
    }

    public StoryResponse markStoryViewed(String storyId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        Story story = resolveActiveStory(storyId);

        if (!caller.getId().equals(normalizeId(story.getAuthor_id()))) {
            story = upsertViewerView(story, caller.getId());
            story = storyRepository.save(story);
        }

        return toStoryResponse(story, loadAuthor(story.getAuthor_id()), caller.getId());
    }

    public StoryResponse setStoryLiked(String storyId, String callerPrincipal, boolean liked) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        Story story = resolveActiveStory(storyId);

        if (caller.getId().equals(normalizeId(story.getAuthor_id()))) {
            throw new ForbiddenOperationException("You cannot like your own story");
        }

        story = upsertViewerReaction(story, caller.getId(), liked);
        story = storyRepository.save(story);
        return toStoryResponse(story, loadAuthor(story.getAuthor_id()), caller.getId());
    }

    public StoryViewersResponse getStoryViewers(String storyId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        Story story = resolveActiveStory(storyId);

        if (!caller.getId().equals(normalizeId(story.getAuthor_id()))) {
            throw new ForbiddenOperationException("Only the story author can view story viewers");
        }

        List<Story.Viewer> viewers = safeViewers(story);
        Set<String> viewerIds = viewers.stream()
                .map(Story.Viewer::getUser_id)
                .map(this::normalizeId)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> viewersById = new LinkedHashMap<>();
        if (!viewerIds.isEmpty()) {
            userRepository.findAllById(viewerIds).forEach(user -> viewersById.put(user.getId(), user));
        }

        List<StoryViewerResponse> items = viewers.stream()
                .sorted(Comparator.comparing(Story.Viewer::getViewed_at, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(viewer -> toStoryViewerResponse(viewer, viewersById.get(normalizeId(viewer.getUser_id()))))
                .toList();

        return StoryViewersResponse.builder()
                .story_id(story.getId())
                .viewer_count(items.size())
                .viewers(items)
                .build();
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

    private StoryResponse toStoryResponse(Story story, User author, String callerId) {
        StoryResponse.AuthorInfo authorInfo = null;
        if (author != null) {
            authorInfo = StoryResponse.AuthorInfo.builder()
                    .id(author.getId())
                    .username(author.getUsername())
                    .full_name(author.getFull_name())
                    .avatar_url(author.getAvatar_url())
                    .build();
        }

        Story.Viewer viewer = findViewer(story, callerId);

        return StoryResponse.builder()
                .id(story.getId())
                .author(authorInfo)
                .media_url(story.getMedia_url())
                .type(story.getType())
                .created_at(story.getCreated_at())
                .expires_at(story.getExpires_at())
                .viewed_by_me(viewer != null)
                .liked_by_me(viewer != null && STORY_HEART_REACTION.equalsIgnoreCase(normalizeId(viewer.getReaction())))
                .viewer_count(safeViewers(story).size())
                .build();
    }

    private StoryViewerResponse toStoryViewerResponse(Story.Viewer viewer, User user) {
        return StoryViewerResponse.builder()
                .id(user != null ? user.getId() : normalizeId(viewer.getUser_id()))
                .username(user != null ? user.getUsername() : "")
                .full_name(user != null ? user.getFull_name() : "")
                .avatar_url(user != null ? user.getAvatar_url() : "")
                .viewed_at(viewer.getViewed_at())
                .liked(STORY_HEART_REACTION.equalsIgnoreCase(normalizeId(viewer.getReaction())))
                .build();
    }

    private Story resolveActiveStory(String storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        if (story.getExpires_at() != null && story.getExpires_at().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Story has expired: " + storyId);
        }
        return story;
    }

    private Story upsertViewerView(Story story, String viewerId) {
        List<Story.Viewer> viewers = new ArrayList<>(safeViewers(story));
        Story.Viewer existing = null;
        for (Story.Viewer viewer : viewers) {
            if (viewerId.equals(normalizeId(viewer.getUser_id()))) {
                existing = viewer;
                break;
            }
        }

        Instant now = Instant.now();
        if (existing == null) {
            viewers.add(Story.Viewer.builder()
                    .user_id(viewerId)
                    .viewed_at(now)
                    .reaction(null)
                    .build());
        } else {
            existing.setViewed_at(now);
        }

        story.setViewers(viewers);
        return story;
    }

    private Story upsertViewerReaction(Story story, String viewerId, boolean liked) {
        List<Story.Viewer> viewers = new ArrayList<>(safeViewers(story));
        Story.Viewer existing = null;
        for (Story.Viewer viewer : viewers) {
            if (viewerId.equals(normalizeId(viewer.getUser_id()))) {
                existing = viewer;
                break;
            }
        }

        Instant now = Instant.now();
        if (existing == null) {
            viewers.add(Story.Viewer.builder()
                    .user_id(viewerId)
                    .viewed_at(now)
                    .reaction(liked ? STORY_HEART_REACTION : null)
                    .build());
        } else {
            if (existing.getViewed_at() == null) {
                existing.setViewed_at(now);
            }
            existing.setReaction(liked ? STORY_HEART_REACTION : null);
        }

        story.setViewers(viewers);
        return story;
    }

    private Story.Viewer findViewer(Story story, String userId) {
        if (story == null || userId == null || userId.isBlank()) {
            return null;
        }

        for (Story.Viewer viewer : safeViewers(story)) {
            if (userId.equals(normalizeId(viewer.getUser_id()))) {
                return viewer;
            }
        }
        return null;
    }

    private List<Story.Viewer> safeViewers(Story story) {
        if (story == null || story.getViewers() == null) {
            return List.of();
        }
        return story.getViewers().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private User loadAuthor(String authorId) {
        String normalized = normalizeId(authorId);
        if (normalized.isBlank()) {
            return null;
        }
        return userRepository.findById(normalized).orElse(null);
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
}
