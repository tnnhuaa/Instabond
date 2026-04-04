package com.instabond.service;

import com.instabond.dto.*;
import com.instabond.dto.ai.AiImageAnalyzeRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(15);

    // AI-suggestions

    public PostSuggestionResponse getPostSuggestions(MultipartFile imageFile, String email) {
        // Get current user
        User currentUser = resolveUserFromPrincipal(email);
        String currentUserId = currentUser.getId();

        // Upload image to Cloudinary and get URL
        String imageUrl = fileService.uploadImageUrl(imageFile);
        AiImageAnalyzeRequest aiRequest = new AiImageAnalyzeRequest(imageUrl);

        // ------ Call AI API in parallel ------

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

        /* @TODO: Call AI Music Suggestion API in parallel when available
        CompletableFuture<List<Post.MusicSuggestion>> musicFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String musicEndpoint = aiServiceUrl + "/api/ai/suggest-music";
                AiMusicResponse musicResponse = restTemplate.postForObject(musicEndpoint, aiRequest, AiMusicResponse.class);
                return musicResponse != null ? musicResponse.getSuggestions() : new ArrayList<>();
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }, aiExecutor);*/

        // -------------------------------------

        // Combine results
        return CompletableFuture.allOf(tagFuture)
                .thenApply(v -> PostSuggestionResponse.builder()
                        .image_url(imageUrl)
                        .suggested_tags(tagFuture.join())
                        .build())
                .join();

        // @TODO: Include music suggestions when that feature is implemented in the AI service
         /* return CompletableFuture.allOf(tagFuture, musicFuture)
                    .thenApply(v -> PostSuggestionResponse.builder()
                        .image_url(imageUrl)
                        .suggested_tags(tagFuture.join())
                        .music_suggestions(musicFuture.join())
                        .build())
                .join();*/
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

    // Helper

    private List<TaggedUserDTO> processAiTags(AiTagResponse aiTagResponse, String currentUserId) {
        if (aiTagResponse == null || aiTagResponse.getDetected_faces() == null) {
            return new ArrayList<>();
        }

        return aiTagResponse.getDetected_faces().stream()
                // Filter confidence > 0.7
                .filter(face -> face.getConfidence() != null && face.getConfidence() > 0.5)
                .filter(face -> !face.getMatched_user_id().equals(currentUserId))
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
                taggedUsers.putIfAbsent(targetUser.getId(), Post.TaggedUser.builder().user_id(targetUser.getId()).build());
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
                taggedUsers.putIfAbsent(targetUser.getId(), Post.TaggedUser.builder().user_id(targetUser.getId()).build());
            }
        }

        return new ArrayList<>(taggedUsers.values());
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
        sendTagNotifications(authorId, savedPost.getId(), extractTaggedUserIds(savedPost.getTagged_users()));
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
        User caller = resolveUserFromPrincipal(callerPrincipal);
        int safePage = sanitizePage(page);
        int safeSize = sanitizeSize(size);

        // Find users the caller is following
        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                Criteria.where("status").is("accepted")));

        // Use relationships collection to get recipient_ids
        List<String> validAuthorIds = new ArrayList<>();
        validAuthorIds.add(caller.getId()); // Include their own posts

        List<java.util.Map> rels = mongoTemplate.find(followingQuery, java.util.Map.class, "relationships");
        for (java.util.Map map : rels) {
            Object recId = map.get("recipient_id");
            if (recId != null) {
                validAuthorIds.add(recId.toString());
            }
        }

        // Build criteria for 'in' clause. Author ID could be stored as String or
        // ObjectId
        List<Object> inClauseArgs = new ArrayList<>();
        for (String aid : validAuthorIds) {
            inClauseArgs.add(aid);
            try {
                inClauseArgs.add(new ObjectId(aid));
            } catch (Exception ignored) {
            }
        }

        Query postQuery = new Query(Criteria.where("author_id").in(inClauseArgs))
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .with(org.springframework.data.domain.PageRequest.of(safePage, safeSize));

        return mongoTemplate.find(postQuery, Post.class).stream()
                .map(post -> {
                    User author = resolveAuthorById(post.getAuthor_id());
                    return toPostResponse(post, author, caller);
                }).toList();
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
        return findPostsByAuthorId(author.getId(), page, size).stream()
                .map(post -> toPostResponse(post, author, caller))
                .toList();
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
        return findPostsByAuthorId(author.getId(), page, size).stream()
                .map(post -> toPostResponse(post, author, caller))
                .toList();
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
        return findPostsByAuthorId(author.getId(), page, size).stream()
                .map(post -> toPostResponse(post, author, caller))
                .toList();
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
        List<String> postIds = bookmarks.stream().map(Interaction::getTarget_id).toList();

        return postIds.stream()
                .map(pid -> postRepository.findById(pid).orElse(null))
                .filter(p -> p != null)
                .map(post -> {
                    User author = resolveAuthorById(post.getAuthor_id());
                    return toPostResponse(post, author, caller);
                })
                .toList();
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

    // Map Post entity to PostResponse DTO
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

        return PostResponse.builder()
                .id(post.getId())
                .author(authorInfo)
                .caption(post.getCaption())
                .location(post.getLocation())
                .media(post.getMedia())
                .music_suggestion(post.getMusic_suggestion())
                .tagged_users(post.getTagged_users())
                .stats(post.getStats())
                .created_at(post.getCreated_at())
                .isLiked(isLiked)
                .isBookmarked(isBookmarked)
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
}
