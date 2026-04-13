package com.instabond.service;

import com.instabond.dto.UserMeResponse;
import com.instabond.dto.ChangePasswordRequest;
import com.instabond.dto.FollowUserResponse;
import com.instabond.dto.ProfileResponse;
import com.instabond.dto.ProfileShareResponse;
import com.instabond.dto.UpdateAllowTaggingResponse;
import com.instabond.dto.UpdateProfileRequest;
import com.instabond.entity.Interaction;
import com.instabond.entity.Post;
import com.instabond.entity.Relationship;
import com.instabond.entity.User;
import com.instabond.exception.AiServiceException;
import com.instabond.exception.ForbiddenOperationException;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.RelationshipRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RelationshipRepository relationshipRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    // GET id
    public String getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }

    // Face detection

    public void registerFace(String email, List<MultipartFile> images) {
        if (images == null || images.size() != 3) {
            throw new IllegalArgumentException("Required exactly 3 images for face registration (front, left, right).");
        }

        User user = resolveUserFromPrincipal(email);

        // Upload images to Cloudinary
        ExecutorService ioExecutor = Executors.newFixedThreadPool(10);

        // Parallel upload using CompletableFuture
        List<CompletableFuture<String>> uploadFutures = images.stream()
                .map(image -> CompletableFuture.supplyAsync(() -> {
                    return fileService.uploadImageUrl(image);
                }, ioExecutor))
                .collect(Collectors.toList());

        List<String> uploadedUrls = uploadFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Interact with AI service to get face embeddings
        com.instabond.dto.ai.FaceEmbeddingRequest aiRequest = new com.instabond.dto.ai.FaceEmbeddingRequest(uploadedUrls);
        String endpoint = aiServiceUrl + "/api/ai/embeddings";
        com.instabond.dto.ai.FaceEmbeddingResponse aiResponse;

        try {
            aiResponse = restTemplate.postForObject(endpoint, aiRequest, com.instabond.dto.ai.FaceEmbeddingResponse.class);
        } catch (Exception e) {
            throw new AiServiceException("AI Microservice is currently unavailable: " + e.getMessage());
        }

        if (aiResponse == null || aiResponse.getAverage_embedding() == null) {
            throw new AiServiceException("AI Service returned an empty embedding result.");
        }

        List<Double> finalEmbedding = aiResponse.getAverage_embedding();

        // Save to database
        user.setRegistrationImageUrls(uploadedUrls);
        user.setFaceEmbedding(finalEmbedding);
        userRepository.save(user);
    }

    // Used by GET /api/users/me

    public UserMeResponse getMe(String email) {
        User user = resolveUserFromPrincipal(email);
        ProfileResponse profile = toProfileResponse(user);

        return UserMeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone_number(user.getPhone_number())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .bio(user.getBio())
                .posts_count(profile.getPosts_count())
                .followers_count(profile.getFollowers_count())
                .following_count(profile.getFollowing_count())
                .is_private(user.getSettings() != null && Boolean.TRUE.equals(user.getSettings().getIs_private()))
                .badges(user.getBadges())
                .settings(user.getSettings())
                .created_at(user.getCreated_at())
                .build();
    }

    // Profile queries

    public List<ProfileResponse> getAllUsers(int page, int limit) {
        int safePage = sanitizePage(page);
        int safeLimit = sanitizeLimit(limit);
        org.springframework.data.domain.Pageable pageable = PageRequest.of(safePage, safeLimit);
        return userRepository.findAll(pageable).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    public ProfileShareResponse getMyShareProfile(String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        return toProfileShareResponse(ensureQrCodeUid(caller));
    }

    public ProfileShareResponse getShareProfile(String userId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        String targetId = "me".equalsIgnoreCase(userId) ? caller.getId() : userId;
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));
        if (!caller.getId().equals(target.getId()) && isBlocked(caller.getId(), target.getId())) {
            throw new ResourceNotFoundException("User not found: " + targetId);
        }
        return toProfileShareResponse(ensureQrCodeUid(target));
    }

    public ProfileResponse resolveProfilePayload(String payload, String callerPrincipal) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload is required");
        }
        String normalized = payload.trim();
        User target = resolveTargetFromPayload(normalized);
        return toProfileResponseWithStatus(target, callerPrincipal);
    }

    public ProfileResponse resolveProfileQuery(String userId, String username, String qrUid, String payload, String callerPrincipal) {
        User target;
        if (payload != null && !payload.isBlank()) {
            target = resolveTargetFromPayload(payload.trim());
        } else if (qrUid != null && !qrUid.isBlank()) {
            target = userRepository.findByQrCodeUid(qrUid.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + qrUid));
        } else if (username != null && !username.isBlank()) {
            target = userRepository.findByUsername(username.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        } else if (userId != null && !userId.isBlank()) {
            target = userRepository.findById(userId.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        } else {
            throw new IllegalArgumentException("At least one of userId, username, qrUid or payload is required");
        }
        return toProfileResponseWithStatus(target, callerPrincipal);
    }

    // Profile updates

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.getFull_name() != null)
            user.setFull_name(request.getFull_name());
        if (request.getBio() != null)
            user.setBio(request.getBio());
        if (request.getPhone_number() != null)
            user.setPhone_number(request.getPhone_number());

        if (request.getSettings() != null) {
            User.Setting setting = user.getSettings() != null ? user.getSettings() : new User.Setting();
            if (request.getSettings().getAllow_tagging() != null)
                setting.setAllow_tagging(request.getSettings().getAllow_tagging());
            if (request.getSettings().getIs_private() != null)
                setting.setIs_private(request.getSettings().getIs_private());
            if (request.getSettings().getTheme() != null)
                setting.setTheme(request.getSettings().getTheme());
            user.setSettings(setting);
        }

        return toProfileResponse(userRepository.save(user));
    }

    public ProfileResponse updateAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setAvatar_url(fileService.uploadImageUrl(file));
        return toProfileResponse(userRepository.save(user));
    }

    private boolean isPrivateAccount(User user) {
        return user.getSettings() != null && Boolean.TRUE.equals(user.getSettings().getIs_private());
    }

    public void updateLastActive(String email, Instant lastActive) {
        if (email == null || email.isBlank())
            return;

        Query query = new Query(Criteria.where("email").is(email));
        Update update = new Update().set("last_active", lastActive);

        mongoTemplate.updateFirst(query, update, User.class);
    }

    // Device token
    public void addDeviceToken(String callerPrincipal, String token) {
        if (token == null || token.isBlank())
            return;

        User user = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(Criteria.where("_id").is(user.getId()));
        Update update = new Update().addToSet("device_tokens", token);

        mongoTemplate.updateFirst(query, update, User.class);
    }

    public void removeDeviceToken(String callerPrincipal, String token) {
        if (token == null || token.isBlank())
            return;

        User user = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(Criteria.where("_id").is(user.getId()));
        Update update = new Update().pull("device_tokens", token);

        mongoTemplate.updateFirst(query, update, User.class);
    }

    // Social graph
    private java.util.Set<String> getMyCloseFriendIds(String callerId) {
        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", callerId),
                Criteria.where("status").is("accepted"),
                Criteria.where("type").is("close_friend"))); // Lọc những người có type là close_friend
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(Relationship::getRecipient_id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private java.util.Set<String> getMyFollowingUserIds(String callerId) {
        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", callerId),
                Criteria.where("status").is("accepted")));
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(Relationship::getRecipient_id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private java.util.Set<String> getMyFollowerUserIds(String callerId) {
        Query query = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", callerId),
                Criteria.where("status").is("accepted")));
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(Relationship::getRequester_id)
                .collect(java.util.stream.Collectors.toSet());
    }

    public List<FollowUserResponse> getFollowers(String userId, String callerPrincipal) {
        return getFollowers(userId, callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getFollowers(String userId, String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        String targetId = "me".equalsIgnoreCase(userId) ? caller.getId() : userId;
        userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", targetId),
                Criteria.where("status").is("accepted"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        java.util.Set<String> myFollowing = getMyFollowingUserIds(caller.getId());
        java.util.Set<String> myFollowers = getMyFollowerUserIds(caller.getId());
        java.util.Set<String> myCloseFriends = getMyCloseFriendIds(caller.getId());
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> buildFollowUserResponse(userRepository.findById(rel.getRequester_id()).orElse(null),
                        myFollowing, myFollowers, myCloseFriends))
                .filter(r -> r != null)
                .toList();
    }

    public List<FollowUserResponse> getFollowing(String userId, String callerPrincipal) {
        return getFollowing(userId, callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getFollowing(String userId, String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        String targetId = "me".equalsIgnoreCase(userId) ? caller.getId() : userId;
        userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", targetId),
                Criteria.where("status").is("accepted"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        java.util.Set<String> myFollowing = getMyFollowingUserIds(caller.getId());
        java.util.Set<String> myFollowers = getMyFollowerUserIds(caller.getId());
        java.util.Set<String> myCloseFriends = getMyCloseFriendIds(caller.getId());
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> buildFollowUserResponse(userRepository.findById(rel.getRecipient_id()).orElse(null),
                        myFollowing, myFollowers, myCloseFriends))
                .filter(r -> r != null)
                .toList();
    }

    public List<FollowUserResponse> getFriends(String userId, String callerPrincipal) {
        return getFriends(userId, callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getFriends(String userId, String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        String targetId = "me".equalsIgnoreCase(userId) ? caller.getId() : userId;
        userRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));

        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", targetId),
                Criteria.where("status").is("accepted")));
        List<String> followingIds = mongoTemplate.find(followingQuery, Relationship.class).stream()
                .map(Relationship::getRecipient_id)
                .toList();

        Query followersQuery = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", targetId),
                Criteria.where("status").is("accepted"),
                Criteria.where("requester_id").in(followingIds))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(followersQuery, page, limit);

        java.util.Set<String> myFollowing = getMyFollowingUserIds(caller.getId());
        java.util.Set<String> myFollowers = getMyFollowerUserIds(caller.getId());
        java.util.Set<String> myCloseFriends = getMyCloseFriendIds(caller.getId());
        return mongoTemplate.find(followersQuery, Relationship.class).stream()
                .map(rel -> buildFollowUserResponse(userRepository.findById(rel.getRequester_id()).orElse(null),
                        myFollowing, myFollowers, myCloseFriends))
                .filter(r -> r != null)
                .toList();
    }

    public FollowUserResponse followUser(String targetUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        if (caller.getId().equals(target.getId())) {
            throw new ForbiddenOperationException("Forbidden - you cannot follow yourself");
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), target.getId()),
                Relationship.class);

        String nextStatus = isPrivateAccount(target) ? "pending" : "accepted";
        Instant now = Instant.now();

        if (relationship == null) {
            relationship = Relationship.builder()
                    .requester_id(caller.getId())
                    .recipient_id(target.getId())
                    .status(nextStatus)
                    .type("follow")
                    .friendship_level("normal")
                    .intimacy_score(0)
                    .created_at(now)
                    .updated_at(now)
                    .build();
        } else {
            relationship.setStatus(nextStatus);
            relationship.setType("follow");
            if (relationship.getFriendship_level() == null || relationship.getFriendship_level().isBlank()) {
                relationship.setFriendship_level("normal");
            }
            relationship.setUpdated_at(now);
        }

        relationshipRepository.save(relationship);

        // Public follow is immediate; private follow creates a pending request.
        String notificationStatus = "pending".equals(nextStatus) ? "pending" : "followed";
        notificationService.sendFollowNotification(caller.getId(), target.getId(), notificationStatus);

        return toFollowUserResponse(target, nextStatus);
    }

    public void unfollowUser(String targetUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        if (caller.getId().equals(targetUserId)) {
            throw new ForbiddenOperationException("Forbidden - you cannot unfollow yourself");
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), targetUserId),
                Relationship.class);

        if (relationship == null) {
            throw new ResourceNotFoundException("Relationship not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public void removeFollower(String followerUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(followerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + followerUserId));

        if (caller.getId().equals(followerUserId)) {
            throw new ForbiddenOperationException("Forbidden - you cannot remove yourself");
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(followerUserId, caller.getId()),
                Relationship.class);

        if (relationship == null) {
            throw new ResourceNotFoundException("Relationship not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public List<FollowUserResponse> getIncomingFollowRequests(String callerPrincipal) {
        return getIncomingFollowRequests(callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getIncomingFollowRequests(String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", caller.getId()),
                Criteria.where("status").is("pending"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRequester_id()).orElse(null),
                        rel.getStatus()))
                .filter(r -> r != null)
                .toList();
    }

    public List<FollowUserResponse> getSentFollowRequests(String callerPrincipal) {
        return getSentFollowRequests(callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getSentFollowRequests(String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                Criteria.where("status").is("pending"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRecipient_id()).orElse(null),
                        rel.getStatus()))
                .filter(r -> r != null)
                .toList();
    }

    public void cancelSentFollowRequest(String recipientId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + recipientId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), recipientId),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new ResourceNotFoundException("Pending follow request not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public FollowUserResponse acceptFollowRequest(String requesterId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(requester.getId(), caller.getId()),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new ResourceNotFoundException("Follow request not found");
        }

        relationship.setStatus("accepted");
        relationship.setUpdated_at(Instant.now());
        relationshipRepository.save(relationship);

        // Send notification to requester that their follow request was accepted
        notificationService.sendFollowNotification(caller.getId(), requester.getId(), "accepted");

        return toFollowUserResponse(requester, "accepted");
    }

    public void rejectFollowRequest(String requesterId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(requesterId, caller.getId()),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new ResourceNotFoundException("Follow request not found");
        }

        relationship.setStatus("rejected");
        relationship.setType("follow");
        relationship.setFriendship_level("normal");
        relationship.setUpdated_at(Instant.now());
        relationshipRepository.save(relationship);
    }

    public FollowUserResponse setCloseFriend(String targetUserId, String callerPrincipal, boolean isCloseFriend) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), target.getId()),
                Relationship.class);

        if (relationship == null || !"accepted".equalsIgnoreCase(relationship.getStatus())) {
            throw new ResourceNotFoundException("Relationship not found");
        }

        relationship.setType(isCloseFriend ? "close_friend" : "follow");
        relationship.setFriendship_level(isCloseFriend ? "close_friend" : "normal");
        relationship.setUpdated_at(Instant.now());
        relationshipRepository.save(relationship);

        return toFollowUserResponse(target, relationship.getStatus());
    }

    public List<FollowUserResponse> getCloseFriends(String userId, String callerPrincipal) {
        return getCloseFriends(userId, callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getCloseFriends(String userId, String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId),
                Criteria.where("status").is("accepted"),
                Criteria.where("type").is("close_friend"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        java.util.Set<String> myFollowing = getMyFollowingUserIds(caller.getId());
        java.util.Set<String> myFollowers = getMyFollowerUserIds(caller.getId());
        java.util.Set<String> myCloseFriends = getMyCloseFriendIds(caller.getId());
        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> buildFollowUserResponse(
                        userRepository.findById(rel.getRecipient_id()).orElse(null),
                        myFollowing,
                        myFollowers,
                        myCloseFriends))
                .filter(r -> r != null)
                .toList();
    }

    // Mappers

    private User resolveUserFromPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Invalid user principal");
        }
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .or(() -> userRepository.findById(principal))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal));
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

    private Query relationshipQuery(String requesterId, String recipientId) {
        Criteria requester = idCriteria("requester_id", requesterId);
        Criteria recipient = idCriteria("recipient_id", recipientId);
        return new Query(new Criteria().andOperator(requester, recipient));
    }

    private ProfileResponse toProfileResponse(User user) {
        Query postsQuery = new Query(idCriteria("author_id", user.getId()));
        long postsCount = mongoTemplate.count(postsQuery, Post.class);

        Query followersQuery = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", user.getId()),
                Criteria.where("status").is("accepted")));
        long followersCount = mongoTemplate.count(followersQuery, Relationship.class);

        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", user.getId()),
                Criteria.where("status").is("accepted")));
        long followingCount = mongoTemplate.count(followingQuery, Relationship.class);

        boolean isPrivate = user.getSettings() != null && Boolean.TRUE.equals(user.getSettings().getIs_private());

        return ProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .full_name(user.getFull_name())
                .bio(user.getBio())
                .avatar_url(user.getAvatar_url())
                .phone_number(user.getPhone_number())
                .posts_count(postsCount)
                .followers_count(followersCount)
                .following_count(followingCount)
                .is_private(isPrivate)
                .badges(user.getBadges())
                .settings(user.getSettings())
                .created_at(user.getCreated_at())
                .build();
    }

    private FollowUserResponse toFollowUserResponse(User user) {
        return toFollowUserResponse(user, null);
    }

    private FollowUserResponse toFollowUserResponse(User user, String relationshipStatus) {
        if (user == null)
            return null;
        return FollowUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .relationship_status(relationshipStatus)
                .is_mutual_follow(false)
                .build();
    }

    private FollowUserResponse buildFollowUserResponse(User user, java.util.Set<String> myFollowing,
            java.util.Set<String> myFollowers, java.util.Set<String> myCloseFriends) {
        if (user == null)
            return null;

        String relationshipStatus = "none";
        boolean isFollowingMe = false;

        if (myFollowing.contains(user.getId())) {
            relationshipStatus = "accepted";
        }

        if (myFollowers.contains(user.getId())) {
            isFollowingMe = true;
        }

        return FollowUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .relationship_status(relationshipStatus)
                .is_mutual_follow(relationshipStatus.equals("accepted") && isFollowingMe)
                .is_close_friend(myCloseFriends.contains(user.getId()))
                .build();
    }

    public ProfileResponse updateMyPrivacy(String callerPrincipal, Boolean isPrivate) {
        if (isPrivate == null) {
            throw new IllegalArgumentException("is_private is required");
        }

        User user = resolveUserFromPrincipal(callerPrincipal);
        User.Setting setting = user.getSettings() != null ? user.getSettings() : new User.Setting();

        boolean wasPrivate = Boolean.TRUE.equals(setting.getIs_private());
        setting.setIs_private(isPrivate);
        user.setSettings(setting);
        User savedUser = userRepository.save(user);

        // When turning public, accept all pending requests to this account.
        if (wasPrivate && !isPrivate) {
            Query pendingQuery = new Query(new Criteria().andOperator(
                    idCriteria("recipient_id", savedUser.getId()),
                    Criteria.where("status").is("pending")));

            List<Relationship> pendingRequests = mongoTemplate.find(pendingQuery, Relationship.class);
            Instant now = Instant.now();
            for (Relationship relationship : pendingRequests) {
                relationship.setStatus("accepted");
                if (relationship.getType() == null || relationship.getType().isBlank()) {
                    relationship.setType("follow");
                }
                if (relationship.getFriendship_level() == null || relationship.getFriendship_level().isBlank()) {
                    relationship.setFriendship_level("normal");
                }
                relationship.setUpdated_at(now);
            }
            if (!pendingRequests.isEmpty()) {
                relationshipRepository.saveAll(pendingRequests);
            }
        }

        return toProfileResponse(savedUser);
    }

    public void changeMyPassword(String callerPrincipal, ChangePasswordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String currentPassword = request.getCurrentPassword() != null ? request.getCurrentPassword().trim() : "";
        String newPassword = request.getNewPassword() != null ? request.getNewPassword().trim() : "";
        String confirmNewPassword = request.getConfirmNewPassword() != null ? request.getConfirmNewPassword().trim() : "";

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            throw new IllegalArgumentException("Current password, new password and confirmation are required");
        }
        if (!newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }

        User user = resolveUserFromPrincipal(callerPrincipal);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public UpdateAllowTaggingResponse updateMyAllowTagging(String callerPrincipal, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!"everyone".equals(normalized) && !"none".equals(normalized)) {
            throw new IllegalArgumentException("value must be one of: everyone, none");
        }

        User user = resolveUserFromPrincipal(callerPrincipal);
        User.Setting setting = user.getSettings() != null ? user.getSettings() : new User.Setting();
        setting.setAllow_tagging(normalized);
        user.setSettings(setting);
        userRepository.save(user);

        return UpdateAllowTaggingResponse.builder()
                .allow_tagging(normalized)
                .build();
    }

    private ProfileResponse toProfileResponseWithStatus(User target, String callerPrincipal) {
        ProfileResponse response = toProfileResponse(target);

        if (callerPrincipal == null || callerPrincipal.isBlank()) {
            response.setRelationship_status("none");
            return response;
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);

        if (caller.getId().equals(target.getId())) {
            response.setRelationship_status("self");
            return response;
        }

        if (isBlocked(caller.getId(), target.getId())) {
            throw new ResourceNotFoundException("User not found: " + target.getId());
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), target.getId()),
                Relationship.class);

        if (relationship != null) {
            response.setRelationship_status(relationship.getStatus());
        } else {
            response.setRelationship_status("none");
        }

        return response;
    }

    // Block User

    public void blockUser(String targetUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        if (caller.getId().equals(target.getId())) {
            throw new ForbiddenOperationException("Forbidden - you cannot block yourself");
        }

        // Remove any existing follow relationships in both directions
        Relationship callerToTarget = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), target.getId()), Relationship.class);
        if (callerToTarget != null) {
            relationshipRepository.deleteById(callerToTarget.getId());
        }

        Relationship targetToCaller = mongoTemplate.findOne(
                relationshipQuery(target.getId(), caller.getId()), Relationship.class);
        if (targetToCaller != null) {
            relationshipRepository.deleteById(targetToCaller.getId());
        }

        // Remove bookmarks both ways so blocked users do not remain in bookmark flows.
        removeBookmarksBetweenUsers(caller.getId(), target.getId());

        // Create block relationship
        Relationship blockRel = Relationship.builder()
                .requester_id(caller.getId())
                .recipient_id(target.getId())
                .status("blocked")
                .type("block")
                .friendship_level("none")
                .intimacy_score(0)
                .created_at(Instant.now())
                .updated_at(Instant.now())
                .build();
        relationshipRepository.save(blockRel);
    }

    public void unblockUser(String targetUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                idCriteria("recipient_id", targetUserId),
                Criteria.where("status").is("blocked")));

        Relationship block = mongoTemplate.findOne(query, Relationship.class);
        if (block == null) {
            throw new ResourceNotFoundException("Block relationship not found");
        }

        relationshipRepository.deleteById(block.getId());
    }

    public List<FollowUserResponse> getBlockedUsers(String callerPrincipal) {
        return getBlockedUsers(callerPrincipal, DEFAULT_PAGE, DEFAULT_LIMIT);
    }

    public List<FollowUserResponse> getBlockedUsers(String callerPrincipal, int page, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                Criteria.where("status").is("blocked")))
                .with(Sort.by(Sort.Direction.DESC, "updated_at"));
        applyPaging(query, page, limit);

        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> toFollowUserResponse(
                        userRepository.findById(rel.getRecipient_id()).orElse(null), "blocked"))
                .filter(r -> r != null)
                .toList();
    }

    public boolean isBlocked(String userId1, String userId2) {
        Query q1 = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId1),
                idCriteria("recipient_id", userId2),
                Criteria.where("status").is("blocked")));
        Query q2 = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId2),
                idCriteria("recipient_id", userId1),
                Criteria.where("status").is("blocked")));
        return mongoTemplate.exists(q1, Relationship.class) || mongoTemplate.exists(q2, Relationship.class);
    }

    // Friend Suggestions

    public List<FollowUserResponse> getFriendSuggestions(String callerPrincipal, int limit) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        int safeLimit = sanitizeLimit(limit);
        java.util.Set<String> myFollowing = getMyFollowingUserIds(caller.getId());

        java.util.Set<String> suggestions = new java.util.LinkedHashSet<>();

        for (String followingId : myFollowing) {
            java.util.Set<String> theirFollowing = getMyFollowingUserIds(followingId);
            for (String candidate : theirFollowing) {
                if (!candidate.equals(caller.getId())
                        && !myFollowing.contains(candidate)
                        && !isBlocked(caller.getId(), candidate)) {
                    suggestions.add(candidate);
                }
                if (suggestions.size() >= safeLimit) {
                    break;
                }
            }
            if (suggestions.size() >= safeLimit) {
                break;
            }
        }

        if (suggestions.size() < safeLimit) {
            Query randomQuery = new Query()
                    .with(PageRequest.of(0, safeLimit * 2));
            List<User> randomUsers = mongoTemplate.find(randomQuery, User.class);
            for (User u : randomUsers) {
                if (!u.getId().equals(caller.getId())
                        && !myFollowing.contains(u.getId())
                        && !isBlocked(caller.getId(), u.getId())
                        && !suggestions.contains(u.getId())) {
                    suggestions.add(u.getId());
                }
                if (suggestions.size() >= safeLimit) {
                    break;
                }
            }
        }

        java.util.Set<String> myFollowers = getMyFollowerUserIds(caller.getId());
        java.util.Set<String> myCloseFriends = getMyCloseFriendIds(caller.getId());

        return suggestions.stream()
                .map(uid -> userRepository.findById(uid).orElse(null))
                .filter(u -> u != null)
                .map(u -> buildFollowUserResponse(u, myFollowing, myFollowers, myCloseFriends))
                .filter(r -> r != null)
                .toList();
    }

    private ProfileShareResponse toProfileShareResponse(User user) {
        String qrUid = user.getQr_code_uid();
        String deepLink = "instabond://profile?uid=" + qrUid;
        String shareText = "Check out @" + user.getUsername() + " on Instabond: " + deepLink;

        return ProfileShareResponse.builder()
                .user_id(user.getId())
                .username(user.getUsername())
                .qr_code_uid(qrUid)
                .deep_link(deepLink)
                .share_text(shareText)
                .build();
    }

    private User ensureQrCodeUid(User user) {
        if (user.getQr_code_uid() != null && !user.getQr_code_uid().isBlank()) {
            return user;
        }
        user.setQr_code_uid("qr_" + UUID.randomUUID());
        return userRepository.save(user);
    }

    private User resolveTargetFromPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload is required");
        }

        String trimmed = payload.trim();
        String deepLinkFromText = extractDeepLinkFromText(trimmed);
        if (deepLinkFromText != null) {
            trimmed = deepLinkFromText;
        }

        if (trimmed.toLowerCase(Locale.ROOT).startsWith("instabond://")) {
            String uid = getQueryParam(trimmed, "uid");
            if (uid != null && !uid.isBlank()) {
                return userRepository.findByQrCodeUid(uid)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uid));
            }
            String userId = getQueryParam(trimmed, "userId");
            if (userId != null && !userId.isBlank()) {
                return userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            }
            String username = getQueryParam(trimmed, "username");
            if (username != null && !username.isBlank()) {
                return userRepository.findByUsername(username)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
            }
            throw new ResourceNotFoundException("Cannot resolve profile from deep-link");
        }

        if (trimmed.toLowerCase(Locale.ROOT).startsWith("qr:")) {
            String qrUid = trimmed.substring(3).trim();
            return userRepository.findByQrCodeUid(qrUid)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + qrUid));
        }

        if (trimmed.contains("uid=") || trimmed.contains("userId=") || trimmed.contains("username=")) {
            return resolveTargetFromPayload("instabond://profile?" + trimmed);
        }

        final String normalizedPayload = trimmed;

        return userRepository.findByQrCodeUid(normalizedPayload)
                .or(() -> userRepository.findById(normalizedPayload))
                .or(() -> userRepository.findByUsername(normalizedPayload))
                .or(() -> userRepository.findByEmail(normalizedPayload))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalizedPayload));
    }

    private String extractDeepLinkFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(?i)(instabond://[^\\s]+)").matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String candidate = matcher.group(1);
        while (!candidate.isEmpty()) {
            char last = candidate.charAt(candidate.length() - 1);
            if (Character.isLetterOrDigit(last) || last == '/' || last == '?' || last == '&' || last == '=' || last == '_' || last == '-') {
                break;
            }
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        return candidate.isBlank() ? null : candidate;
    }

    private void removeBookmarksBetweenUsers(String userAId, String userBId) {
        Set<String> postsByA = getAuthoredPostIds(userAId);
        Set<String> postsByB = getAuthoredPostIds(userBId);

        removeBookmarksForAuthorPosts(userAId, postsByB);
        removeBookmarksForAuthorPosts(userBId, postsByA);
    }

    private Set<String> getAuthoredPostIds(String authorId) {
        Query query = new Query(idCriteria("author_id", authorId));
        query.fields().include("_id");

        return mongoTemplate.find(query, Post.class).stream()
                .map(Post::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void removeBookmarksForAuthorPosts(String bookmarkOwnerId, Set<String> targetPostIds) {
        if (targetPostIds == null || targetPostIds.isEmpty()) {
            return;
        }

        Query deleteQuery = new Query(new Criteria().andOperator(
                idCriteria("user_id", bookmarkOwnerId),
                Criteria.where("type").is("bookmark"),
                Criteria.where("target_type").is("post"),
                idInCriteria("target_id", targetPostIds)));

        mongoTemplate.remove(deleteQuery, Interaction.class);
    }

    private Criteria idInCriteria(String field, Collection<String> ids) {
        List<String> stringIds = ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (stringIds.isEmpty()) {
            return Criteria.where(field).in(Collections.emptyList());
        }

        List<ObjectId> objectIds = new ArrayList<>();
        for (String id : stringIds) {
            try {
                objectIds.add(new ObjectId(id));
            } catch (Exception ignored) {
            }
        }

        if (objectIds.isEmpty()) {
            return Criteria.where(field).in(stringIds);
        }

        return new Criteria().orOperator(
                Criteria.where(field).in(stringIds),
                Criteria.where(field).in(objectIds));
    }

    private String getQueryParam(String uriString, String key) {
        try {
            URI uri = URI.create(uriString);
            String query = uri.getQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && key.equals(parts[0])) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int sanitizePage(int page) {
        return page < 0 ? DEFAULT_PAGE : page;
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void applyPaging(Query query, int page, int limit) {
        query.with(PageRequest.of(sanitizePage(page), sanitizeLimit(limit)));
    }

    public ProfileResponse getProfile(String userId, String callerPrincipal) {
        if ("me".equalsIgnoreCase(userId)) {
            return toProfileResponseWithStatus(resolveUserFromPrincipal(callerPrincipal), callerPrincipal);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return toProfileResponseWithStatus(user, callerPrincipal);
    }

    public ProfileResponse getProfileByUsername(String username, String callerPrincipal) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toProfileResponseWithStatus(user, callerPrincipal);
    }
}
