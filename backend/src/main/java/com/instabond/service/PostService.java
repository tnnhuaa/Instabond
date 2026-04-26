package com.instabond.service;

import com.instabond.dto.*;
import com.instabond.dto.ai.AiImageAnalyzeRequest;
import com.instabond.dto.ai.AiMusicResponse;
import com.instabond.dto.ai.AiTagResponse;
import com.instabond.entity.Interaction;
import com.instabond.entity.Post;
import com.instabond.entity.User;
import com.instabond.exception.ForbiddenOperationException;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.InteractionRepository;
import com.instabond.repository.PostRepository;
import com.instabond.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.bson.types.ObjectId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final RestTemplate restTemplate;
    private final MongoTemplate mongoTemplate;
    private final InteractionRepository interactionRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${app.ai-detection.confidence-threshold:0.5}")
    private Double confidenceThreshold;

    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(15);

    // AI-suggestions

    public PostSuggestionResponse getPostSuggestions(MultipartFile imageFile, String email) {
        // Get current user
        User currentUser = resolveUserFromPrincipal(email);
        String currentUserId = currentUser.getId();

        // Upload image to Cloudinary and get URL
        String imageUrl = fileService.uploadImageUrl(imageFile);
        AiImageAnalyzeRequest aiRequest = new AiImageAnalyzeRequest(imageUrl);

        // Call AI Tag Suggestion API
        CompletableFuture<List<TaggedUserDTO>> tagFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String tagEndpoint = aiServiceUrl + "/api/ai/suggest-tags";
                AiTagResponse tagResponse = restTemplate.postForObject(tagEndpoint, aiRequest, AiTagResponse.class);
                return processAiTags(tagResponse, currentUserId);
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }, aiExecutor);

        // Call AI Music Suggestion API
        CompletableFuture<AiMusicResponse> musicFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String musicEndpoint = aiServiceUrl + "/api/ai/suggest-music";
                AiMusicResponse musicResponse = restTemplate.postForObject(musicEndpoint, aiRequest, AiMusicResponse.class);
                return musicResponse != null
                        ? musicResponse
                        : AiMusicResponse.builder().suggestions(Collections.emptyList()).build();
            } catch (Exception e) {
                return AiMusicResponse.builder().suggestions(Collections.emptyList()).build();
            }
        }, aiExecutor);

        return CompletableFuture.allOf(tagFuture, musicFuture)
                .thenApply(v -> {
                    AiMusicResponse musicResponse = musicFuture.join();
                    return PostSuggestionResponse.builder()
                            .image_url(imageUrl)
                            .scene_description(musicResponse.getScene_description())
                            .suggested_tags(tagFuture.join())
                            .music_suggestions(musicResponse.getSuggestions() != null
                                    ? musicResponse.getSuggestions()
                                    : Collections.emptyList())
                            .build();
                })
                .join();
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

    private User resolveAuthorById(String authorId) {
        if (authorId == null) {
            return null;
        }
        return userRepository.findById(authorId).orElse(null);
    }

    private List<TaggedUserDTO> processAiTags(AiTagResponse aiTagResponse, String currentUserId) {
        if (aiTagResponse == null || aiTagResponse.getDetected_faces() == null) {
            return new ArrayList<>();
        }

        return aiTagResponse.getDetected_faces().stream()
                .filter(face -> face.getConfidence() != null && face.getConfidence() > confidenceThreshold)
                .filter(face -> !face.getMatched_user_id().equals(currentUserId))
                .collect(Collectors.toMap(
                        face -> face.getMatched_user_id(),
                        face -> face,
                        (face1, face2) -> face1.getConfidence() > face2.getConfidence() ? face1 : face2
                ))

                .values().stream()
                .map(face -> {
                    User user = userRepository.findById(face.getMatched_user_id()).orElse(null);
                    if (user == null) return null;

                    return TaggedUserDTO.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .full_name(user.getFull_name())
                            .avatar_url(user.getAvatar_url())
                            .confidence(face.getConfidence())
                            .position(face.getPosition())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Post> findPostsByAuthorId(String authorId) {
        return findPostsByAuthorId(authorId, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    private List<Post> findPostsByAuthorId(String authorId, int page, int size) {
        ObjectId oid;
        try {
            oid = new ObjectId(authorId);
        } catch (Exception e) {
            return List.of();
        }

        Query query = new Query(
                new Criteria().orOperator(
                        Criteria.where("author_id").is(oid),
                        Criteria.where("author_id").is(authorId)))
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .with(org.springframework.data.domain.PageRequest.of(sanitizePage(page), sanitizeSize(size)));
        return mongoTemplate.find(query, Post.class);
    }

    private User resolveTaggedUser(String rawTaggedValue) {
        if (rawTaggedValue == null || rawTaggedValue.isBlank()) {
            return null;
        }

        String normalized = rawTaggedValue.trim();
        User userById = userRepository.findById(normalized).orElse(null);
        if (userById != null) {
            return userById;
        }

        return userRepository.findByUsername(normalized).orElse(null);
    }

    private List<Post.TaggedUser> processTaggedUsers(String callerId, List<CreatePostRequest.TaggedUserRequest> requestTaggedUsers) {
        Map<String, Post.TaggedUser> taggedUsers = new LinkedHashMap<>();
        if (requestTaggedUsers == null || requestTaggedUsers.isEmpty()) {
            return new ArrayList<>();
        }

        for (CreatePostRequest.TaggedUserRequest requestTaggedUser : requestTaggedUsers) {
            User targetUser = resolveTaggedUser(requestTaggedUser != null ? requestTaggedUser.getUser_id() : null);
            if (targetUser == null) {
                continue;
            }

            if (targetUser.getId().equals(callerId)) {
                taggedUsers.putIfAbsent(targetUser.getId(), buildTaggedUser(targetUser.getId(), requestTaggedUser));
                continue;
            }

            // Check for block status
            if (userService.isBlocked(callerId, targetUser.getId())) {
                continue;
            }

            // Check allow_tagging setting
            String allowTagging = (targetUser.getSettings() != null && targetUser.getSettings().getAllow_tagging() != null)
                    ? targetUser.getSettings().getAllow_tagging().toLowerCase()
                    : "everyone";

            if (!"none".equals(allowTagging)) {
                taggedUsers.putIfAbsent(targetUser.getId(), buildTaggedUser(targetUser.getId(), requestTaggedUser));
            }
        }

        return new ArrayList<>(taggedUsers.values());
    }

    private Post.TaggedUser buildTaggedUser(String userId, CreatePostRequest.TaggedUserRequest requestTaggedUser) {
        Post.TaggedUser.Position position = null;
        if (requestTaggedUser != null && requestTaggedUser.getPosition() != null) {
            position = Post.TaggedUser.Position.builder()
                    .x(requestTaggedUser.getPosition().getX())
                    .y(requestTaggedUser.getPosition().getY())
                    .build();
        }

        return Post.TaggedUser.builder()
                .user_id(userId)
                .tag_type(requestTaggedUser != null ? requestTaggedUser.getTag_type() : null)
                .confidence(requestTaggedUser != null ? requestTaggedUser.getConfidence() : 0.0)
                .position(position)
                .build();
    }

    private Set<String> extractTaggedUserIds(List<Post.TaggedUser> taggedUsers) {
        Set<String> ids = new LinkedHashSet<>();
        if (taggedUsers == null || taggedUsers.isEmpty()) {
            return ids;
        }

        for (Post.TaggedUser taggedUser : taggedUsers) {
            if (taggedUser == null || taggedUser.getUser_id() == null || taggedUser.getUser_id().isBlank()) {
                continue;
            }
            ids.add(taggedUser.getUser_id());
        }
        return ids;
    }

    private void sendTagNotifications(String senderId, String postId, Set<String> recipientIds) {
        if (postId == null || postId.isBlank() || recipientIds == null || recipientIds.isEmpty()) {
            return;
        }

        for (String recipientId : recipientIds) {
            if (recipientId == null || recipientId.isBlank() || recipientId.equals(senderId)) {
                continue;
            }
            notificationService.sendTagNotification(senderId, recipientId, postId);
        }
    }

    // Create a new post
    public PostResponse createPost(String callerEmail, CreatePostRequest request, List<MultipartFile> files) {
        User author = resolveUserFromPrincipal(callerEmail);
        String authorId = author.getId();

        CreatePostRequest payload = request != null ? request : new CreatePostRequest();

        List<Post.Media> mediaList = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            if (files.size() > 10) {
                throw new IllegalArgumentException("A post can contain at most 10 images");
            }
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                UploadResponse uploaded = fileService.uploadImage(file);
                mediaList.add(Post.Media.builder()
                        .url(uploaded.getUrl())
                        .width(uploaded.getWidth())
                        .height(uploaded.getHeight())
                        .build());
            }
            if (mediaList.isEmpty()) {
                throw new IllegalArgumentException("At least 1 valid image is required when `files` is provided");
            }
        } else if (payload.getMedia() != null && !payload.getMedia().isEmpty()) {
            if (payload.getMedia().size() > 10) {
                throw new IllegalArgumentException("A post can contain at most 10 media items");
            }
            for (CreatePostRequest.MediaRequest m : payload.getMedia()) {
                if (m.getUrl() == null || m.getUrl().isBlank()) {
                    throw new IllegalArgumentException("Each media item must have a non-empty url");
                }
                mediaList.add(Post.Media.builder()
                        .url(m.getUrl().trim())
                        .width(m.getWidth())
                        .height(m.getHeight())
                        .build());
            }
        }

        Post.Location location = null;
        if (payload.getLocation() != null) {
            location = Post.Location.builder()
                    .name(payload.getLocation().getName())
                    .coordinates(payload.getLocation().getCoordinates())
                    .build();
        }

        Post.MusicSuggestion musicSuggestion = null;
        if (payload.getMusic_suggestion() != null) {
            musicSuggestion = Post.MusicSuggestion.builder()
                    .song_name(payload.getMusic_suggestion().getSong_name())
                    .artist(payload.getMusic_suggestion().getArtist())
                    .preview_url(payload.getMusic_suggestion().getPreview_url())
                    .build();
        }

        List<Post.TaggedUser> taggedUsers = processTaggedUsers(authorId, payload.getTagged_users());

        Post post = Post.builder()
                .author_id(authorId)
                .caption(payload.getCaption())
                .location(location)
                .media(mediaList)
                .music_suggestion(musicSuggestion)
                .tagged_users(taggedUsers)
                .stats(Post.Stats.builder().likes(0).comments(0).shares(0).build())
                .created_at(Instant.now())
                .build();

        Post savedPost = postRepository.save(post);
        Set<String> newTaggedIds = extractTaggedUserIds(savedPost.getTagged_users());

        sendTagNotifications(authorId, savedPost.getId(), newTaggedIds);

        for (String taggedId : newTaggedIds) {
            eventPublisher.publishEvent(new UserInteractionDTO(authorId, taggedId, "TAG"));
        }

        return toPostResponse(savedPost, author, author);
    }

    // Get a single post by ID
    public PostResponse getPostById(String postId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        User author = resolveAuthorById(post.getAuthor_id());
        User caller = resolveUserFromPrincipal(callerPrincipal);
        assertCanViewAuthorContent(author, callerPrincipal);
        return toPostResponse(post, author, caller);
    }

    // Get all posts sorted by newest first
    public List<PostResponse> getFeed(String callerPrincipal, int page, int size) {
        return getFeed(callerPrincipal, page, size, "following");
    }

    public List<PostResponse> getFeed(String callerPrincipal, int page, int size, String mode) {
        return getFeed(callerPrincipal, page, size, mode, null);
    }

    public List<PostResponse> getFeed(String callerPrincipal, int page, int size, String mode, Long seed) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        int safePage = sanitizePage(page);
        int safeSize = sanitizeSize(size);
        String normalizedMode = mode == null ? "following" : mode.trim().toLowerCase(Locale.ROOT);

        if ("for_you".equals(normalizedMode)) {
            return getForYouFeed(caller, safePage, safeSize, seed);
        }

        return getFollowingFeed(caller, safePage, safeSize);
    }

    private List<PostResponse> getFollowingFeed(User caller, int safePage, int safeSize) {
        Set<String> acceptedFollowing = getAcceptedFollowingIds(caller.getId());

        List<String> validAuthorIds = new ArrayList<>();
        validAuthorIds.add(caller.getId());
        validAuthorIds.addAll(acceptedFollowing);

        List<Object> inClauseArgs = new ArrayList<>();
        for (String aid : validAuthorIds) {
            inClauseArgs.add(aid);
            try {
                inClauseArgs.add(new ObjectId(aid));
            } catch (Exception ignored) {}
        }

        Query postQuery = new Query(Criteria.where("author_id").in(inClauseArgs))
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .with(org.springframework.data.domain.PageRequest.of(safePage, safeSize));

        List<Post> posts = mongoTemplate.find(postQuery, Post.class);
        return toPostResponses(posts, caller);
    }

    private List<PostResponse> getForYouFeed(User caller, int safePage, int safeSize, Long seed) {
        Set<String> acceptedFollowing = getAcceptedFollowingIds(caller.getId());
        long effectiveSeed = resolveForYouSeed(caller, seed);

        // Fetch up to 500 posts
        Query postQuery = new Query()
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .limit(500);

        List<Post> allPosts = mongoTemplate.find(postQuery, Post.class);

        if (allPosts.isEmpty()) {
            return List.of();
        }

        // --- BATCHING VISIBILITY CHECK ---
        Set<String> uniqueAuthorIds = allPosts.stream()
                .map(post -> normalizeId(post.getAuthor_id()))
                .filter(id -> !id.isEmpty() && !id.equals(caller.getId()))
                .collect(Collectors.toSet());

        Map<String, User> authorsMap = userRepository.findAllById(uniqueAuthorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<String> visibleAuthorIds = new HashSet<>();
        for (String authorId : uniqueAuthorIds) {
            User author = authorsMap.get(authorId);
            if (author == null) continue;

            if (userService.isBlocked(caller.getId(), authorId)) {
                continue;
            }

            boolean isPrivate = author.getSettings() != null && Boolean.TRUE.equals(author.getSettings().getIs_private());
            if (isPrivate && !acceptedFollowing.contains(authorId)) {
                continue;
            }

            visibleAuthorIds.add(authorId);
        }

        List<Post> visiblePosts = allPosts.stream()
                .filter(post -> visibleAuthorIds.contains(normalizeId(post.getAuthor_id())))
                .collect(Collectors.toCollection(ArrayList::new));
        // --------------------

        if (visiblePosts.isEmpty()) {
            return List.of();
        }

        List<Post> followingPosts = new ArrayList<>();
        List<Post> discoverPosts = new ArrayList<>();

        for (Post post : visiblePosts) {
            if (acceptedFollowing.contains(normalizeId(post.getAuthor_id()))) {
                followingPosts.add(post);
            } else {
                discoverPosts.add(post);
            }
        }

        followingPosts.sort(buildForYouComparator(effectiveSeed, caller.getId()));
        discoverPosts.sort(buildForYouComparator(effectiveSeed ^ 0x9E3779B97F4A7C15L, caller.getId()));

        int followingQuota = Math.max(1, (int) Math.floor(safeSize * 0.3));
        int discoverQuota = Math.max(1, safeSize - followingQuota);

        List<Post> mixed = new ArrayList<>(visiblePosts.size());
        int followCursor = 0;
        int discoverCursor = 0;

        while (followCursor < followingPosts.size() || discoverCursor < discoverPosts.size()) {
            int followPick = 0;
            while (followPick < followingQuota && followCursor < followingPosts.size()) {
                mixed.add(followingPosts.get(followCursor++));
                followPick++;
            }

            int discoverPick = 0;
            while (discoverPick < discoverQuota && discoverCursor < discoverPosts.size()) {
                mixed.add(discoverPosts.get(discoverCursor++));
                discoverPick++;
            }

            if (followCursor >= followingPosts.size() && discoverCursor >= discoverPosts.size()) {
                break;
            }
        }

        int fromIndex = safePage * safeSize;
        if (fromIndex >= mixed.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + safeSize, mixed.size());
        List<Post> paginatedPosts = mixed.subList(fromIndex, toIndex);

        return toPostResponses(paginatedPosts, caller);
    }

    private boolean isVisibleForForYou(User caller, Post post) {
        String authorId = normalizeId(post.getAuthor_id());
        if (authorId.isEmpty() || authorId.equals(caller.getId())) {
            return false;
        }

        if (userService.isBlocked(caller.getId(), authorId)) {
            return false;
        }

        User author = resolveAuthorById(authorId);
        if (author == null) {
            return false;
        }

        return !isPrivateAuthor(author) || hasAcceptedFollow(caller.getId(), authorId);
    }

    private long resolveForYouSeed(User caller, Long seed) {
        if (seed != null && seed != 0L) {
            return seed;
        }

        long callerHash = Math.abs((long) Objects.hashCode(caller.getId()));
        long dayBucket = Instant.now().getEpochSecond() / 86_400L;
        return callerHash ^ dayBucket;
    }

    private Comparator<Post> buildForYouComparator(long seed, String callerId) {
        return Comparator.comparingLong((Post post) -> stableForYouRank(post, seed, callerId))
                .thenComparing(Post::getCreated_at, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private long stableForYouRank(Post post, long seed, String callerId) {
        String postId = normalizeId(post.getId());
        String authorId = normalizeId(post.getAuthor_id());
        long base = Objects.hash(postId, authorId, callerId, seed);
        long mixed = base * 0x9E3779B97F4A7C15L;
        return mixed ^ (mixed >>> 33);
    }

    private Set<String> getAcceptedFollowingIds(String requesterId) {
        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", requesterId),
                Criteria.where("status").is("accepted")));

        return mongoTemplate.find(followingQuery, java.util.Map.class, "relationships").stream()
                .map(map -> map.get("recipient_id"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .map(this::normalizeId)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Get all posts by userId
    public List<PostResponse> getPostsByUserId(String userId, String callerPrincipal) {
        return getPostsByUserId(userId, callerPrincipal, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public List<PostResponse> getPostsByUserId(String userId, String callerPrincipal, int page, int size) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        User caller = resolveUserFromPrincipal(callerPrincipal);
        assertCanViewAuthorContent(author, callerPrincipal);

        List<Post> posts = findPostsByAuthorId(author.getId(), page, size);
        return toPostResponses(posts, caller);
    }

    // Get all posts by username
    public List<PostResponse> getPostsByUsername(String username, String callerPrincipal) {
        return getPostsByUsername(username, callerPrincipal, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public List<PostResponse> getPostsByUsername(String username, String callerPrincipal, int page, int size) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        User caller = resolveUserFromPrincipal(callerPrincipal);
        assertCanViewAuthorContent(author, callerPrincipal);

        List<Post> posts = findPostsByAuthorId(author.getId(), page, size);
        return toPostResponses(posts, caller);
    }

    // Get all posts by email
    public List<PostResponse> getPostsByEmail(String email, String callerPrincipal) {
        return getPostsByEmail(email, callerPrincipal, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public List<PostResponse> getPostsByEmail(String email, String callerPrincipal, int page, int size) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        User caller = resolveUserFromPrincipal(callerPrincipal);
        assertCanViewAuthorContent(author, callerPrincipal);

        List<Post> posts = findPostsByAuthorId(author.getId(), page, size);
        return toPostResponses(posts, caller);
    }

    // Update post fields (only the author is allowed)
    public PostResponse updatePost(String postId, String callerEmail, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerEmail);

        // Compare using normalized string IDs
        String postAuthorId = post.getAuthor_id();
        String callerId = caller.getId();
        if (!normalizeId(postAuthorId).equals(normalizeId(callerId))) {
            throw new ForbiddenOperationException("Forbidden - you are not the author of this post");
        }

        if (request.getCaption() != null)
            post.setCaption(request.getCaption());
        if (request.getLocation() != null) {
            post.setLocation(Post.Location.builder()
                    .name(request.getLocation().getName())
                    .coordinates(request.getLocation().getCoordinates())
                    .build());
        }

        Set<String> existingTaggedIds = extractTaggedUserIds(post.getTagged_users());
        if (request.getTagged_users() != null) {
            List<CreatePostRequest.TaggedUserRequest> mappedTagRequests = request.getTagged_users().stream()
                .map(req -> {
                    CreatePostRequest.TaggedUserRequest tReq = new CreatePostRequest.TaggedUserRequest();
                    tReq.setUser_id(req.getUser_id());
                    tReq.setTag_type(req.getTag_type());
                    tReq.setConfidence(req.getConfidence());
                    tReq.setPosition(req.getPosition());
                    return tReq;
                })
                .toList();
            List<Post.TaggedUser> processedTags = processTaggedUsers(callerId, mappedTagRequests);
            post.setTagged_users(processedTags);
        }

        Post savedPost = postRepository.save(post);
        if (request.getTagged_users() != null) {
            Set<String> newTaggedIds = extractTaggedUserIds(savedPost.getTagged_users());
            newTaggedIds.removeAll(existingTaggedIds);
            sendTagNotifications(callerId, savedPost.getId(), newTaggedIds);
        }

        return toPostResponse(savedPost, caller, caller);
    }

    // Delete a post (only the author is allowed)
    public void deletePost(String postId, String callerEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerEmail);

        // Compare using normalized string IDs
        if (!normalizeId(post.getAuthor_id()).equals(normalizeId(caller.getId()))) {
            throw new ForbiddenOperationException("Forbidden - you are not the author of this post");
        }

        postRepository.deleteById(postId);
    }

    public PostResponse likePost(String postId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        boolean alreadyLiked = interactionRepository
                .findOne(caller.getId(), postId, "post", "like")
                .isPresent();

        if (!alreadyLiked) {
            Interaction interaction = Interaction.builder()
                    .user_id(caller.getId())
                    .target_id(postId)
                    .target_type("post")
                    .type("like")
                    .created_at(Instant.now())
                    .build();
            interactionRepository.save(interaction);
            incrementPostStat(postId, "stats.likes", 1);

            // Send notification to post author
            String postAuthorId = post.getAuthor_id();
            if (postAuthorId != null && !postAuthorId.equals(caller.getId())) {
                notificationService.sendLikeNotification(caller.getId(), postAuthorId, postId);
                eventPublisher.publishEvent(new UserInteractionDTO(caller.getId(), postAuthorId, "LIKE"));
            }
        }

        return getPostById(postId, callerPrincipal);
    }

    public PostResponse sharePost(String postId, String callerPrincipal) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);

        // Allow multiple shares — each share creates a new interaction
        Interaction interaction = Interaction.builder()
                .user_id(caller.getId())
                .target_id(postId)
                .target_type("post")
                .type("share")
                .created_at(Instant.now())
                .build();
        interactionRepository.save(interaction);
        incrementPostStat(postId, "stats.shares", 1);

        return getPostById(postId, callerPrincipal);
    }

    public PostResponse unsharePost(String postId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);

        interactionRepository.findOne(caller.getId(), postId, "post", "share")
                .ifPresent(interaction -> {
                    interactionRepository.deleteById(interaction.getId());
                    int currentShares = post.getStats() != null ? post.getStats().getShares() : 0;
                    if (currentShares > 0) {
                        incrementPostStat(postId, "stats.shares", -1);
                    }
                });

        return getPostById(postId, callerPrincipal);
    }

    public PostResponse unlikePost(String postId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        interactionRepository.findOne(caller.getId(), postId, "post", "like")
                .ifPresent(interaction -> {
                    interactionRepository.deleteById(interaction.getId());
                    int currentLikes = post.getStats() != null ? post.getStats().getLikes() : 0;
                    if (currentLikes > 0) {
                        incrementPostStat(postId, "stats.likes", -1);
                    }

                    String postAuthorId = post.getAuthor_id();
                    if (postAuthorId != null && !postAuthorId.equals(caller.getId())) {
                        eventPublisher.publishEvent(new UserInteractionDTO(caller.getId(), postAuthorId, "UNLIKE"));
                    }
                });

        return getPostById(postId, callerPrincipal);
    }

    // Bookmark
    public PostResponse bookmarkPost(String postId, String callerPrincipal) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        boolean alreadyBookmarked = interactionRepository
                .findOne(caller.getId(), postId, "post", "bookmark")
                .isPresent();

        if (!alreadyBookmarked) {
            Interaction interaction = Interaction.builder()
                    .user_id(caller.getId())
                    .target_id(postId)
                    .target_type("post")
                    .type("bookmark")
                    .created_at(Instant.now())
                    .build();
            interactionRepository.save(interaction);
        }

        return getPostById(postId, callerPrincipal);
    }

    public PostResponse unbookmarkPost(String postId, String callerPrincipal) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        interactionRepository.findOne(caller.getId(), postId, "post", "bookmark")
                .ifPresent(interaction -> interactionRepository.deleteById(interaction.getId()));

        return getPostById(postId, callerPrincipal);
    }

    public List<PostResponse> getBookmarkedPosts(String callerPrincipal) {
        return getBookmarkedPosts(callerPrincipal, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public List<PostResponse> getBookmarkedPosts(String callerPrincipal, int page, int size) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("user_id").is(caller.getId()),
                Criteria.where("target_type").is("post"),
                Criteria.where("type").is("bookmark")))
                .with(Sort.by(Sort.Direction.DESC, "created_at"));

        query.with(org.springframework.data.domain.PageRequest.of(sanitizePage(page), sanitizeSize(size)));

        List<Interaction> bookmarks = mongoTemplate.find(query, Interaction.class);
        if (bookmarks.isEmpty()) {
            return List.of();
        }

        List<String> postIds = bookmarks.stream().map(Interaction::getTarget_id).toList();
        List<Post> posts = postRepository.findByIdIn(postIds);

        // Pre-fetch authors to check blocks
        Set<String> authorIds = posts.stream().map(Post::getAuthor_id).collect(Collectors.toSet());
        Map<String, User> authorsMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Post> visiblePosts = new ArrayList<>();
        for (Post post : posts) {
            User author = authorsMap.get(normalizeId(post.getAuthor_id()));
            if (author == null) continue;

            if (userService.isBlocked(caller.getId(), author.getId())) {
                bookmarks.stream()
                        .filter(b -> b.getTarget_id().equals(post.getId()))
                        .findFirst()
                        .ifPresent(b -> interactionRepository.deleteById(b.getId()));
                continue;
            }
            visiblePosts.add(post);
        }

        // Maintain bookmark sorted order
        Map<String, Post> postMap = visiblePosts.stream().collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> sortedVisiblePosts = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .toList();

        return toPostResponses(sortedVisiblePosts, caller);
    }

    public CommentResponse addComment(String postId, String callerPrincipal, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content is required");
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        Interaction interaction = Interaction.builder()
                .user_id(caller.getId())
                .target_id(postId)
                .target_type("post")
                .type("comment")
                .parent_id(request.getParent_id())
                .reaction_icon(request.getReaction_icon())
                .content(request.getContent().trim())
                .created_at(Instant.now())
                .build();

        Interaction saved = interactionRepository.save(interaction);
        incrementPostStat(postId, "stats.comments", 1);

        // Send notification
        String postAuthorId = post.getAuthor_id();
        if (request.getParent_id() != null && !request.getParent_id().isBlank()) {
            // This is a reply to a comment
            Interaction parentComment = interactionRepository.findById(request.getParent_id())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found: " + request.getParent_id()));
            String parentCommentAuthorId = parentComment.getUser_id();

            if (parentCommentAuthorId != null && !parentCommentAuthorId.equals(caller.getId())) {
                notificationService.sendReplyCommentNotification(caller.getId(), parentCommentAuthorId, postId, request.getParent_id(), request.getContent());
            }
        } else {
            // This is a top-level comment on a post
            if (postAuthorId != null && !postAuthorId.equals(caller.getId())) {
                notificationService.sendCommentNotification(caller.getId(), postAuthorId, postId, request.getContent());
            }
        }

        if (postAuthorId != null && !postAuthorId.equals(caller.getId())) {
            Query query = new Query(new Criteria().andOperator(
                    Criteria.where("user_id").is(caller.getId()),
                    Criteria.where("target_id").is(postId),
                    Criteria.where("target_type").is("post"),
                    Criteria.where("type").is("comment")
            ));

            long userCommentCount = mongoTemplate.count(query, Interaction.class);
            if (userCommentCount == 1) {
                eventPublisher.publishEvent(new UserInteractionDTO(caller.getId(), postAuthorId, "COMMENT"));
            }
        }

        return toCommentResponse(saved, caller, 0, false);
    }

    public List<CommentResponse> getComments(String postId, String callerPrincipal) {
        return getComments(postId, callerPrincipal, DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public List<CommentResponse> getComments(String postId, String callerPrincipal, int page, int size) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        List<Interaction> comments = interactionRepository.findByTargetAndType(postId, "post", "comment");
        Set<String> userIds = comments.stream()
                .map(Interaction::getUser_id)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> usersById = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> usersById.put(user.getId(), user));
        }
        
        Map<String, Long> likeCounts = new LinkedHashMap<>();
        Map<String, Boolean> isLikedByMe = new LinkedHashMap<>();
        
        List<String> commentIds = comments.stream().map(Interaction::getId).toList();
        if (!commentIds.isEmpty()) {
            Query likesQuery = new Query(new Criteria().andOperator(
                Criteria.where("target_id").in(commentIds),
                Criteria.where("target_type").is("comment"),
                Criteria.where("type").is("like")
            ));
            List<Interaction> likesForComments = mongoTemplate.find(likesQuery, Interaction.class);

            for (Interaction like : likesForComments) {
                likeCounts.put(like.getTarget_id(), likeCounts.getOrDefault(like.getTarget_id(), 0L) + 1);
            }

            if (callerPrincipal != null && !callerPrincipal.isBlank()) {
                try {
                    User caller = resolveUserFromPrincipal(callerPrincipal);
                    for (Interaction like : likesForComments) {
                        if (caller.getId().equals(like.getUser_id())) {
                            isLikedByMe.put(like.getTarget_id(), true);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<CommentResponse> allComments = comments.stream()
                .map(comment -> toCommentResponse(comment, usersById.get(comment.getUser_id()),
                     likeCounts.getOrDefault(comment.getId(), 0L).intValue(), 
                     isLikedByMe.getOrDefault(comment.getId(), false)))
                .collect(Collectors.toList());

        List<CommentResponse> topLevel = new ArrayList<>();
        Map<String, List<CommentResponse>> repliesMap = new java.util.HashMap<>();

        for (CommentResponse c : allComments) {
            if (c.getParent_id() == null || c.getParent_id().trim().isEmpty()) {
                topLevel.add(c);
            } else {
                repliesMap.computeIfAbsent(c.getParent_id(), k -> new ArrayList<>()).add(c);
            }
        }

        topLevel.sort((a, b) -> b.getCreated_at().compareTo(a.getCreated_at()));

        List<CommentResponse> sortedComments = new ArrayList<>();
        for (CommentResponse parent : topLevel) {
            sortedComments.add(parent);
            flattenReplies(parent.getId(), repliesMap, sortedComments);
        }

        int from = sanitizePage(page) * sanitizeSize(size);
        if (from >= sortedComments.size()) {
            return List.of();
        }
        int to = Math.min(from + sanitizeSize(size), sortedComments.size());
        return sortedComments.subList(from, to);
    }

    private void flattenReplies(String commentId, Map<String, List<CommentResponse>> repliesMap, List<CommentResponse> result) {
        List<CommentResponse> children = repliesMap.getOrDefault(commentId, new ArrayList<>());
        children.sort((a, b) -> a.getCreated_at().compareTo(b.getCreated_at()));
        for (CommentResponse child : children) {
            result.add(child);
            flattenReplies(child.getId(), repliesMap, result);
        }
    }

    public void deleteComment(String postId, String commentId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        Interaction comment = interactionRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!postId.equals(comment.getTarget_id()) || !"post".equals(comment.getTarget_type())
                || !"comment".equals(comment.getType())) {
            throw new ResourceNotFoundException("Comment not found: " + commentId);
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        if (!caller.getId().equals(comment.getUser_id())) {
            throw new ForbiddenOperationException("Forbidden - you are not the author of this comment");
        }

        interactionRepository.deleteById(commentId);
        int currentComments = post.getStats() != null ? post.getStats().getComments() : 0;
        if (currentComments > 0) {
            incrementPostStat(postId, "stats.comments", -1);
        }

        String postAuthorId = post.getAuthor_id();
        if (postAuthorId != null && !postAuthorId.equals(caller.getId())) {
            Query query = new Query(new Criteria().andOperator(
                    Criteria.where("user_id").is(caller.getId()),
                    Criteria.where("target_id").is(postId),
                    Criteria.where("target_type").is("post"),
                    Criteria.where("type").is("comment")
            ));

            long remainingComments = mongoTemplate.count(query, Interaction.class);
            if (remainingComments == 0) {
                eventPublisher.publishEvent(new UserInteractionDTO(caller.getId(), postAuthorId, "DELETE_COMMENT"));
            }
        }
    }

    public void likeComment(String postId, String commentId, String callerPrincipal) {
        Interaction comment = interactionRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        boolean alreadyLiked = interactionRepository
                .findOne(caller.getId(), commentId, "comment", "like")
                .isPresent();

        if (!alreadyLiked) {
            Interaction interaction = Interaction.builder()
                    .user_id(caller.getId())
                    .target_id(commentId)
                    .target_type("comment")
                    .type("like")
                    .created_at(Instant.now())
                    .build();
            interactionRepository.save(interaction);

            // Send notification to comment author
            String commentAuthorId = comment.getUser_id();
            if (commentAuthorId != null && !commentAuthorId.equals(caller.getId())) {
                notificationService.sendLikeCommentNotification(caller.getId(), commentAuthorId, postId, commentId);
            }
        }
    }

    public void unlikeComment(String postId, String commentId, String callerPrincipal) {
        interactionRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        User caller = resolveUserFromPrincipal(callerPrincipal);
        interactionRepository.findOne(caller.getId(), commentId, "comment", "like")
                .ifPresent(interaction -> interactionRepository.deleteById(interaction.getId()));
    }

    private void incrementPostStat(String postId, String field, int delta) {
        Query query = new Query(Criteria.where("_id").is(postId));
        Update update = new Update().inc(field, delta);
        mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Post.class);
    }

    private CommentResponse toCommentResponse(Interaction interaction, User author, int likesCount, boolean isLiked) {
        CommentResponse.AuthorInfo authorInfo = null;
        if (author != null) {
            authorInfo = CommentResponse.AuthorInfo.builder()
                    .id(author.getId())
                    .username(author.getUsername())
                    .full_name(author.getFull_name())
                    .avatar_url(author.getAvatar_url())
                    .build();
        }

        return CommentResponse.builder()
                .id(interaction.getId())
                .post_id(interaction.getTarget_id())
                .parent_id(interaction.getParent_id())
                .content(interaction.getContent())
                .reaction_icon(interaction.getReaction_icon())
                .author(authorInfo)
                .created_at(interaction.getCreated_at())
                .likes_count(likesCount)
                .is_liked(isLiked)
                .build();
    }

    // Normalize MongoDB ID: strip ObjectId wrapper if present
    private String normalizeId(String id) {
        if (id == null)
            return "";
        return id.trim();
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

    private boolean isPrivateAuthor(User author) {
        return author != null
                && author.getSettings() != null
                && Boolean.TRUE.equals(author.getSettings().getIs_private());
    }

    private boolean hasAcceptedFollow(String requesterId, String recipientId) {
        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", requesterId),
                idCriteria("recipient_id", recipientId),
                Criteria.where("status").is("accepted")));
        return mongoTemplate.exists(query, "relationships");
    }

    private void assertCanViewAuthorContent(User author, String callerPrincipal) {
        if (!isPrivateAuthor(author)) {
            return;
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        if (caller.getId().equals(author.getId())) {
            return;
        }

        if (!hasAcceptedFollow(caller.getId(), author.getId())) {
            throw new ForbiddenOperationException("Forbidden - this account is private");
        }
    }

    private int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    private int sanitizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    // MAP POST ENTITY TO POST RESPONSE
    private PostResponse toPostResponse(Post post, User author, User caller) {
        PostResponse.AuthorInfo authorInfo = null;
        if (author != null) {
            authorInfo = PostResponse.AuthorInfo.builder()
                    .id(author.getId())
                    .username(author.getUsername())
                    .full_name(author.getFull_name())
                    .avatar_url(author.getAvatar_url())
                    .build();
        }

        boolean isLiked = false;
        boolean isBookmarked = false;
        if (caller != null && post.getId() != null) {
            isLiked = interactionRepository
                    .findOne(caller.getId(), post.getId(), "post", "like")
                    .isPresent();
            isBookmarked = interactionRepository
                    .findOne(caller.getId(), post.getId(), "post", "bookmark")
                    .isPresent();
        }

        List<TaggedUserDTO> taggedUserDTOs = mapTaggedUsers(post.getTagged_users());

        return PostResponse.builder()
                .id(post.getId())
                .author(authorInfo)
                .caption(post.getCaption())
                .location(post.getLocation())
                .media(post.getMedia())
                .music_suggestion(post.getMusic_suggestion())
                .tagged_users(taggedUserDTOs)
                .stats(post.getStats())
                .created_at(post.getCreated_at())
                .isLiked(isLiked)
                .isBookmarked(isBookmarked)
                .build();
    }

    private List<TaggedUserDTO> mapTaggedUsers(List<Post.TaggedUser> taggedUsers) {
        if (taggedUsers == null || taggedUsers.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> userIds = taggedUsers.stream()
                .map(Post.TaggedUser::getUser_id)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> userMap.put(user.getId(), user));
        }

        return taggedUsers.stream()
                .map(tu -> {
                    User user = userMap.get(tu.getUser_id());
                    if (user == null) {
                        return null;
                    }

                    Boolean isPrivate = user.getSettings() != null && Boolean.TRUE.equals(user.getSettings().getIs_private());

                    return TaggedUserDTO.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .full_name(user.getFull_name())
                            .avatar_url(user.getAvatar_url())
                            .is_private(isPrivate)
                            .confidence(tu.getConfidence())
                            .position(tu.getPosition())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // BATCH METHOD
    private List<PostResponse> toPostResponses(List<Post> posts, User caller) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> userIds = new HashSet<>();
        List<String> postIds = new ArrayList<>();

        // Gather all unique IDs needed
        for (Post post : posts) {
            postIds.add(post.getId());
            userIds.add(normalizeId(post.getAuthor_id()));
            if (post.getTagged_users() != null) {
                post.getTagged_users().forEach(tu -> {
                    if (tu.getUser_id() != null) userIds.add(tu.getUser_id());
                });
            }
        }

        // Batch fetch Users
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch fetch Interactions (Likes & Bookmarks)
        Set<String> likedPostIds = new HashSet<>();
        Set<String> bookmarkedPostIds = new HashSet<>();

        if (caller != null && !postIds.isEmpty()) {
            Query interactionQuery = new Query(new Criteria().andOperator(
                    Criteria.where("user_id").is(caller.getId()),
                    Criteria.where("target_id").in(postIds),
                    Criteria.where("target_type").is("post"),
                    Criteria.where("type").in("like", "bookmark")
            ));

            List<Interaction> interactions = mongoTemplate.find(interactionQuery, Interaction.class);
            for (Interaction interaction : interactions) {
                if ("like".equals(interaction.getType())) {
                    likedPostIds.add(interaction.getTarget_id());
                } else if ("bookmark".equals(interaction.getType())) {
                    bookmarkedPostIds.add(interaction.getTarget_id());
                }
            }
        }

        // Map in memory
        return posts.stream().map(post -> {
            User author = userMap.get(normalizeId(post.getAuthor_id()));
            boolean isLiked = likedPostIds.contains(post.getId());
            boolean isBookmarked = bookmarkedPostIds.contains(post.getId());
            List<TaggedUserDTO> taggedUserDTOs = mapTaggedUsers(post.getTagged_users(), userMap);

            PostResponse.AuthorInfo authorInfo = null;
            if (author != null) {
                authorInfo = PostResponse.AuthorInfo.builder()
                        .id(author.getId())
                        .username(author.getUsername())
                        .full_name(author.getFull_name())
                        .avatar_url(author.getAvatar_url())
                        .build();
            }

            return PostResponse.builder()
                    .id(post.getId())
                    .author(authorInfo)
                    .caption(post.getCaption())
                    .location(post.getLocation())
                    .media(post.getMedia())
                    .music_suggestion(post.getMusic_suggestion())
                    .tagged_users(taggedUserDTOs)
                    .stats(post.getStats())
                    .created_at(post.getCreated_at())
                    .isLiked(isLiked)
                    .isBookmarked(isBookmarked)
                    .build();
        }).toList();
    }

    // OVERLOAD FOR BATCH PROCESSING
    private List<TaggedUserDTO> mapTaggedUsers(List<Post.TaggedUser> taggedUsers, Map<String, User> userMap) {
        if (taggedUsers == null || taggedUsers.isEmpty()) {
            return new ArrayList<>();
        }

        return taggedUsers.stream()
                .map(tu -> {
                    User user = userMap.get(tu.getUser_id());
                    if (user == null) return null;

                    Boolean isPrivate = user.getSettings() != null && Boolean.TRUE.equals(user.getSettings().getIs_private());

                    return TaggedUserDTO.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .full_name(user.getFull_name())
                            .avatar_url(user.getAvatar_url())
                            .is_private(isPrivate)
                            .confidence(tu.getConfidence())
                            .position(tu.getPosition())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
