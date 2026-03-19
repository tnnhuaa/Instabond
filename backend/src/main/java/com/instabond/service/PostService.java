package com.instabond.service;

import com.instabond.dto.CommentResponse;
import com.instabond.dto.CreateCommentRequest;
import com.instabond.dto.CreatePostRequest;
import com.instabond.dto.PostResponse;
import com.instabond.dto.UpdatePostRequest;
import com.instabond.dto.UploadResponse;
import com.instabond.entity.Interaction;
import com.instabond.entity.Post;
import com.instabond.entity.User;
import com.instabond.repository.InteractionRepository;
import com.instabond.repository.PostRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final MongoTemplate mongoTemplate;
    private final InteractionRepository interactionRepository;

    private User resolveUserFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new RuntimeException("Invalid user principal");
        }

        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .or(() -> userRepository.findById(principal))
                .orElseThrow(() -> new RuntimeException("User not found: " + principal));
    }

    private User resolveAuthorById(String authorId) {
        if (authorId == null) {
            return null;
        }
        return userRepository.findById(authorId).orElse(null);
    }

    // Helper
    private List<Post> findPostsByAuthorId(String authorId) {
        ObjectId oid;
        try {
            oid = new ObjectId(authorId);
        } catch (Exception e) {
            return List.of();
        }

        Query query = new Query(
                new Criteria().orOperator(
                        Criteria.where("author_id").is(oid),
                        Criteria.where("author_id").is(authorId)
                )
        ).with(Sort.by(Sort.Direction.DESC, "created_at"));
        return mongoTemplate.find(query, Post.class);
    }

    // Create a new post
    public PostResponse createPost(String callerEmail, CreatePostRequest request, List<MultipartFile> files) {
        User author = resolveUserFromPrincipal(callerEmail);
        String authorId = author.getId();

        CreatePostRequest payload = request != null ? request : new CreatePostRequest();

        List<Post.Media> mediaList = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            if (files.size() > 10) {
                throw new RuntimeException("A post can contain at most 10 images");
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
                throw new RuntimeException("At least 1 valid image is required when `files` is provided");
            }
        } else if (payload.getMedia() != null && !payload.getMedia().isEmpty()) {
            if (payload.getMedia().size() > 10) {
                throw new RuntimeException("A post can contain at most 10 media items");
            }
            for (CreatePostRequest.MediaRequest m : payload.getMedia()) {
                if (m.getUrl() == null || m.getUrl().isBlank()) {
                    throw new RuntimeException("Each media item must have a non-empty url");
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

        List<Post.TaggedUser> taggedUsers = new ArrayList<>();
        if (payload.getTagged_users() != null) {
            for (CreatePostRequest.TaggedUserRequest t : payload.getTagged_users()) {
                taggedUsers.add(Post.TaggedUser.builder().user_id(t.getUser_id()).build());
            }
        }

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

        return toPostResponse(postRepository.save(post), author);
    }

    // Get a single post by ID
    public PostResponse getPostById(String postId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));
        User author = resolveAuthorById(post.getAuthor_id());
        assertCanViewAuthorContent(author, callerPrincipal);
        return toPostResponse(post, author);
    }

    // Get all posts sorted by newest first
    public List<PostResponse> getFeed(String callerPrincipal, int page, int size) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        
        // Find users the caller is following
        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                Criteria.where("status").is("accepted")
        ));
        
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

        // Build criteria for 'in' clause. Author ID could be stored as String or ObjectId
        List<Object> inClauseArgs = new ArrayList<>();
        for (String aid : validAuthorIds) {
            inClauseArgs.add(aid);
            try {
                inClauseArgs.add(new ObjectId(aid));
            } catch (Exception ignored) {}
        }

        Query postQuery = new Query(Criteria.where("author_id").in(inClauseArgs))
                .with(Sort.by(Sort.Direction.DESC, "created_at"))
                .with(org.springframework.data.domain.PageRequest.of(page, size));

        return mongoTemplate.find(postQuery, Post.class).stream()
                .map(post -> {
                    User author = resolveAuthorById(post.getAuthor_id());
                    return toPostResponse(post, author);
                }).toList();
    }

    // Get all posts by userId
    public List<PostResponse> getPostsByUserId(String userId, String callerPrincipal) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        assertCanViewAuthorContent(author, callerPrincipal);
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Get all posts by username
    public List<PostResponse> getPostsByUsername(String username, String callerPrincipal) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        assertCanViewAuthorContent(author, callerPrincipal);
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Get all posts by email
    public List<PostResponse> getPostsByEmail(String email, String callerPrincipal) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        assertCanViewAuthorContent(author, callerPrincipal);
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Update post fields (only the author is allowed)
    public PostResponse updatePost(String postId, String callerEmail, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerEmail);

        // Compare using normalized string IDs
        String postAuthorId = post.getAuthor_id();
        String callerId = caller.getId();
        if (!normalizeId(postAuthorId).equals(normalizeId(callerId))) {
            throw new RuntimeException("Forbidden — you are not the author of this post");
        }

        if (request.getCaption() != null) post.setCaption(request.getCaption());
        if (request.getLocation() != null) {
            post.setLocation(Post.Location.builder()
                    .name(request.getLocation().getName())
                    .coordinates(request.getLocation().getCoordinates())
                    .build());
        }
        if (request.getTagged_users() != null) {
            post.setTagged_users(request.getTagged_users().stream()
                    .map(t -> Post.TaggedUser.builder().user_id(t.getUser_id()).build())
                    .toList());
        }

        return toPostResponse(postRepository.save(post), caller);
    }

    // Delete a post (only the author is allowed)
    public void deletePost(String postId, String callerEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerEmail);

        // Compare using normalized string IDs
        if (!normalizeId(post.getAuthor_id()).equals(normalizeId(caller.getId()))) {
            throw new RuntimeException("Forbidden — you are not the author of this post");
        }

        postRepository.deleteById(postId);
    }

    public PostResponse likePost(String postId, String callerPrincipal) {
        postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

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
        }

        return getPostById(postId, callerPrincipal);
    }

    public PostResponse sharePost(String postId, String callerPrincipal) {
        postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        User caller = resolveUserFromPrincipal(callerPrincipal);

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
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

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
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

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

    public CommentResponse addComment(String postId, String callerPrincipal, CreateCommentRequest request) {
        postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new RuntimeException("Comment content is required");
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        Interaction interaction = Interaction.builder()
                .user_id(caller.getId())
                .target_id(postId)
                .target_type("post")
                .type("comment")
                .reaction_icon(request.getReaction_icon())
                .content(request.getContent().trim())
                .created_at(Instant.now())
                .build();

        Interaction saved = interactionRepository.save(interaction);
        incrementPostStat(postId, "stats.comments", 1);
        return toCommentResponse(saved, caller);
    }

    public List<CommentResponse> getComments(String postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        List<Interaction> comments = interactionRepository.findByTargetAndType(postId, "post", "comment");
        Set<String> userIds = comments.stream()
                .map(Interaction::getUser_id)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> usersById = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> usersById.put(user.getId(), user));
        }

        return comments.stream()
                .map(comment -> toCommentResponse(comment, usersById.get(comment.getUser_id())))
                .toList();
    }

    public void deleteComment(String postId, String commentId, String callerPrincipal) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        Interaction comment = interactionRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found: " + commentId));

        if (!postId.equals(comment.getTarget_id()) || !"post".equals(comment.getTarget_type()) || !"comment".equals(comment.getType())) {
            throw new RuntimeException("Comment not found: " + commentId);
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        if (!caller.getId().equals(comment.getUser_id())) {
            throw new RuntimeException("Forbidden — you are not the author of this comment");
        }

        interactionRepository.deleteById(commentId);
        int currentComments = post.getStats() != null ? post.getStats().getComments() : 0;
        if (currentComments > 0) {
            incrementPostStat(postId, "stats.comments", -1);
        }
    }

    private void incrementPostStat(String postId, String field, int delta) {
        Query query = new Query(Criteria.where("_id").is(postId));
        Update update = new Update().inc(field, delta);
        mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Post.class);
    }

    private CommentResponse toCommentResponse(Interaction interaction, User author) {
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
                .content(interaction.getContent())
                .reaction_icon(interaction.getReaction_icon())
                .author(authorInfo)
                .created_at(interaction.getCreated_at())
                .build();
    }

    // Normalize MongoDB ID: strip ObjectId wrapper if present
    private String normalizeId(String id) {
        if (id == null) return "";
        return id.trim();
    }

    // Map Post entity to PostResponse DTO
    private PostResponse toPostResponse(Post post, User author) {
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
                .tagged_users(post.getTagged_users())
                .stats(post.getStats())
                .created_at(post.getCreated_at())
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
                Criteria.where("status").is("accepted")
        ));
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
            throw new RuntimeException("Forbidden — this account is private");
        }
    }
}
