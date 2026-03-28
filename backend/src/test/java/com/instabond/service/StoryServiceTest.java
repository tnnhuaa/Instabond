package com.instabond.service;

import com.instabond.dto.StoryResponse;
import com.instabond.entity.Story;
import com.instabond.entity.User;
import com.instabond.repository.StoryRepository;
import com.instabond.repository.UserRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileService fileService;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StoryService storyService;

    @Test
    void createImageStorySavesStoryWith24HourExpiryAndReturnsResponse() {
        User author = User.builder()
                .id("507f1f77bcf86cd799439011")
                .username("alice")
                .full_name("Alice")
                .avatar_url("https://cdn/avatar.jpg")
                .email("alice@example.com")
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "story.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(fileService.uploadImageUrl(file)).thenReturn("https://cdn/story.jpg");
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> {
            Story story = invocation.getArgument(0);
            story.setId("story-1");
            return story;
        });

        StoryResponse response = storyService.createImageStory("alice@example.com", file);

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();

        assertEquals(author.getId(), saved.getAuthor_id());
        assertEquals("https://cdn/story.jpg", saved.getMedia_url());
        assertEquals("image", saved.getType());
        assertEquals(List.of(), saved.getViewers());
        assertNotNull(saved.getCreated_at());
        assertNotNull(saved.getExpires_at());
        assertEquals(Duration.ofHours(24), Duration.between(saved.getCreated_at(), saved.getExpires_at()));

        assertEquals("story-1", response.getId());
        assertEquals("alice", response.getAuthor().getUsername());
        assertEquals("https://cdn/story.jpg", response.getMedia_url());
        assertEquals("image", response.getType());
    }

    @Test
    void createImageStoryPropagatesInvalidFileErrors() {
        User author = User.builder()
                .id("507f1f77bcf86cd799439011")
                .email("alice@example.com")
                .build();
        MultipartFile file = new MockMultipartFile("file", new byte[0]);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(fileService.uploadImageUrl(file)).thenThrow(new IllegalArgumentException("Invalid file type"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> storyService.createImageStory("alice@example.com", file));

        assertEquals("Invalid file type", ex.getMessage());
    }

    @Test
    void getActiveFeedQueriesOnlySelfAndAcceptedFollowingsAndMapsAuthors() {
        String callerId = "507f1f77bcf86cd799439011";
        String followedId = "507f1f77bcf86cd799439012";
        User caller = User.builder().id(callerId).email("alice@example.com").build();
        User followedAuthor = User.builder()
                .id(followedId)
                .username("bob")
                .full_name("Bob")
                .avatar_url("https://cdn/bob.jpg")
                .build();
        Story activeStory = Story.builder()
                .id("story-1")
                .author_id(followedId)
                .media_url("https://cdn/story.jpg")
                .type("image")
                .created_at(Instant.parse("2026-03-27T10:15:30Z"))
                .expires_at(Instant.parse("2026-03-28T10:15:30Z"))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(caller));
        when(mongoTemplate.find(any(Query.class), eq(Map.class), eq("relationships")))
                .thenReturn(List.of(Map.of("recipient_id", followedId)));
        when(mongoTemplate.find(any(Query.class), eq(Story.class)))
                .thenReturn(List.of(activeStory));
        when(userRepository.findAllById(any())).thenReturn(List.of(followedAuthor));

        List<StoryResponse> feed = storyService.getActiveFeed("alice@example.com");

        ArgumentCaptor<Query> relationshipQueryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(relationshipQueryCaptor.capture(), eq(Map.class), eq("relationships"));
        Document relationshipQuery = relationshipQueryCaptor.getValue().getQueryObject();
        List<Document> relationshipConditions = relationshipQuery.getList("$and", Document.class);
        assertEquals("accepted", relationshipConditions.get(1).getString("status"));
        List<Document> requesterConditions = relationshipConditions.getFirst()
                .get("$or", List.class);
        assertTrue(requesterConditions.stream()
                .map(doc -> ((Document) doc).get("requester_id"))
                .anyMatch(value -> callerId.equals(value)));

        ArgumentCaptor<Query> storyQueryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(storyQueryCaptor.capture(), eq(Story.class));
        Document storyQuery = storyQueryCaptor.getValue().getQueryObject();
        List<Document> storyConditions = storyQuery.getList("$and", Document.class);
        Document authorCondition = storyConditions.stream()
                .filter(condition -> condition.containsKey("author_id"))
                .findFirst()
                .orElseThrow();
        Document expiresCondition = storyConditions.stream()
                .filter(condition -> condition.containsKey("expires_at"))
                .findFirst()
                .orElseThrow();
        List<?> authorIds = authorCondition.get("author_id", Document.class).getList("$in", Object.class);
        assertTrue(authorIds.contains(callerId));
        assertTrue(authorIds.contains(followedId));
        assertTrue(authorIds.stream().anyMatch(value -> value instanceof ObjectId));
        assertTrue(expiresCondition.get("expires_at", Document.class).get("$gt") instanceof Instant);
        assertEquals(Document.parse("{ \"created_at\" : -1}"), storyQueryCaptor.getValue().getSortObject());

        assertEquals(1, feed.size());
        assertEquals("story-1", feed.getFirst().getId());
        assertEquals("bob", feed.getFirst().getAuthor().getUsername());
        assertEquals("https://cdn/story.jpg", feed.getFirst().getMedia_url());
    }

    @Test
    void getActiveFeedSupportsStoryAuthorIdsStoredAsObjectIdInstances() {
        String callerId = "507f1f77bcf86cd799439011";
        String followedId = "507f1f77bcf86cd799439012";
        User caller = User.builder().id(callerId).email("alice@example.com").build();
        User followedAuthor = User.builder().id(followedId).username("bob").build();
        Story activeStory = Story.builder()
                .id("story-oid")
                .author_id(new ObjectId(followedId).toHexString())
                .media_url("https://cdn/story-oid.jpg")
                .type("image")
                .created_at(Instant.parse("2026-03-27T10:15:30Z"))
                .expires_at(Instant.parse("2026-03-28T10:15:30Z"))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(caller));
        when(mongoTemplate.find(any(Query.class), eq(Map.class), eq("relationships")))
                .thenReturn(List.of(Map.of("recipient_id", followedId)));
        when(mongoTemplate.find(any(Query.class), eq(Story.class)))
                .thenReturn(List.of(activeStory));
        when(userRepository.findAllById(any())).thenReturn(List.of(followedAuthor));

        List<StoryResponse> feed = storyService.getActiveFeed("alice@example.com");

        assertEquals(1, feed.size());
        assertEquals("story-oid", feed.getFirst().getId());
        assertEquals("bob", feed.getFirst().getAuthor().getUsername());
    }
}
