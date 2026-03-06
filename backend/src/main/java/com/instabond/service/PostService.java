package com.instabond.service;

import com.instabond.dto.CreatePostRequest;
import com.instabond.dto.PostResponse;
import com.instabond.dto.UpdatePostRequest;
import com.instabond.entity.Post;
import com.instabond.entity.User;
import com.instabond.repository.PostRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final MongoTemplate mongoTemplate;

    // Helper: resolve User by email
    private User resolveUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    // Helper: resolve User by ID (returns null if not found)
    private User resolveAuthorById(String authorId) {
        if (authorId == null) return null;
        return userRepository.findById(authorId).orElse(null);
    }

    // Helper
    private List<Post> findPostsByAuthorId(String authorId) {
        ObjectId oid;
        try {
            oid = new ObjectId(authorId);
        } catch (Exception e) {
            log.warn("Invalid ObjectId for author_id: {}", authorId);
            return List.of();
        }
        // Use both ObjectId and String to cover all mapping scenarios
        Query query = new Query(
                new Criteria().orOperator(
                        Criteria.where("author_id").is(oid),
                        Criteria.where("author_id").is(authorId)
                )
        ).with(Sort.by(Sort.Direction.DESC, "created_at"));
        List<Post> posts = mongoTemplate.find(query, Post.class);
        log.info("findPostsByAuthorId({}) => {} posts", authorId, posts.size());
        return posts;
    }

    // Create a new post
    public PostResponse createPost(String callerEmail, CreatePostRequest request, List<MultipartFile> files) {
        User author = resolveUserByEmail(callerEmail);
        String authorId = author.getId();

        List<Post.Media> mediaList = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String url = fileService.uploadImage(file);
                    mediaList.add(Post.Media.builder().url(url).build());
                }
            }
        } else if (request.getMedia() != null) {
            for (CreatePostRequest.MediaRequest m : request.getMedia()) {
                mediaList.add(Post.Media.builder()
                        .url(m.getUrl())
                        .width(m.getWidth())
                        .height(m.getHeight())
                        .build());
            }
        }

        Post.Location location = null;
        if (request.getLocation() != null) {
            location = Post.Location.builder()
                    .name(request.getLocation().getName())
                    .coordinates(request.getLocation().getCoordinates())
                    .build();
        }

        Post.MusicSuggestion musicSuggestion = null;
        if (request.getMusic_suggestion() != null) {
            musicSuggestion = Post.MusicSuggestion.builder()
                    .song_name(request.getMusic_suggestion().getSong_name())
                    .artist(request.getMusic_suggestion().getArtist())
                    .preview_url(request.getMusic_suggestion().getPreview_url())
                    .build();
        }

        List<Post.TaggedUser> taggedUsers = new ArrayList<>();
        if (request.getTagged_users() != null) {
            for (CreatePostRequest.TaggedUserRequest t : request.getTagged_users()) {
                taggedUsers.add(Post.TaggedUser.builder().user_id(t.getUser_id()).build());
            }
        }

        Post post = Post.builder()
                .author_id(authorId)
                .caption(request.getCaption())
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
    public PostResponse getPostById(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));
        User author = resolveAuthorById(post.getAuthor_id());
        return toPostResponse(post, author);
    }

    // Get all posts sorted by newest first
    public List<PostResponse> getFeed() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "created_at"));
        return mongoTemplate.find(query, Post.class).stream()
                .map(post -> {
                    User author = resolveAuthorById(post.getAuthor_id());
                    return toPostResponse(post, author);
                }).toList();
    }

    // Get all posts by userId
    public List<PostResponse> getPostsByUserId(String userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Get all posts by username
    public List<PostResponse> getPostsByUsername(String username) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Get all posts by email
    public List<PostResponse> getPostsByEmail(String email) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return findPostsByAuthorId(author.getId()).stream()
                .map(post -> toPostResponse(post, author))
                .toList();
    }

    // Update post fields (only the author is allowed)
    public PostResponse updatePost(String postId, String callerEmail, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found: " + postId));

        User caller = resolveUserByEmail(callerEmail);

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

        User caller = resolveUserByEmail(callerEmail);

        // Compare using normalized string IDs
        if (!normalizeId(post.getAuthor_id()).equals(normalizeId(caller.getId()))) {
            throw new RuntimeException("Forbidden — you are not the author of this post");
        }

        postRepository.deleteById(postId);
    }

    // Normalize MongoDB ID: strip ObjectId wrapper if present
    private String normalizeId(String id) {
        if (id == null) return "";
        // ObjectId.toString() returns the hex string directly, so just trim
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
}
