package com.instabond.service;

import com.instabond.dto.UserMeResponse;
import com.instabond.dto.FollowUserResponse;
import com.instabond.dto.ProfileResponse;
import com.instabond.dto.UpdateProfileRequest;
import com.instabond.entity.Post;
import com.instabond.entity.Relationship;
import com.instabond.entity.User;
import com.instabond.repository.RelationshipRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RelationshipRepository relationshipRepository;
    private final FileService fileService;
    private final MongoTemplate mongoTemplate;

    // GET id
    public String getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
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
                .is_private(profile.is_private())
                .badges(user.getBadges())
                .settings(user.getSettings())
                .created_at(user.getCreated_at())
                .build();
    }

    // Profile queries

    public List<ProfileResponse> getAllUsers(int page, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit);
        return userRepository.findAll(pageable).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    private boolean hasAcceptedFollow(String requesterId, String recipientId) {
        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", requesterId),
                idCriteria("recipient_id", recipientId),
                Criteria.where("status").is("accepted")));
        return mongoTemplate.exists(query, Relationship.class);
    }

    private void assertCanViewProfile(User target, String callerPrincipal) {
        if (!isPrivateAccount(target)) {
            return;
        }

        User caller = resolveUserFromPrincipal(callerPrincipal);
        if (caller.getId().equals(target.getId())) {
            return;
        }

        if (!hasAcceptedFollow(caller.getId(), target.getId())) {
            throw new RuntimeException("Forbidden — this profile is private");
        }
    }

    public ProfileResponse getProfile(String userId, String callerPrincipal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        assertCanViewProfile(user, callerPrincipal);
        return toProfileResponse(user);
    }

    public ProfileResponse getProfileByUsername(String username, String callerPrincipal) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        assertCanViewProfile(user, callerPrincipal);
        return toProfileResponse(user);
    }

    // Profile updates

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

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
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

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
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", userId),
                Criteria.where("status").is("accepted"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

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
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId),
                Criteria.where("status").is("accepted"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

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
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Query followingQuery = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId),
                Criteria.where("status").is("accepted")));
        List<String> followingIds = mongoTemplate.find(followingQuery, Relationship.class).stream()
                .map(Relationship::getRecipient_id)
                .toList();

        Query followersQuery = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", userId),
                Criteria.where("status").is("accepted"),
                Criteria.where("requester_id").in(followingIds))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

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
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUserId));

        if (caller.getId().equals(target.getId())) {
            throw new RuntimeException("Forbidden — you cannot follow yourself");
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
        return toFollowUserResponse(target, nextStatus);
    }

    public void unfollowUser(String targetUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUserId));

        if (caller.getId().equals(targetUserId)) {
            throw new RuntimeException("Forbidden — you cannot unfollow yourself");
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), targetUserId),
                Relationship.class);

        if (relationship == null) {
            throw new RuntimeException("Relationship not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public void removeFollower(String followerUserId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(followerUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + followerUserId));

        if (caller.getId().equals(followerUserId)) {
            throw new RuntimeException("Forbidden — you cannot remove yourself");
        }

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(followerUserId, caller.getId()),
                Relationship.class);

        if (relationship == null) {
            throw new RuntimeException("Relationship not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public List<FollowUserResponse> getIncomingFollowRequests(String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                idCriteria("recipient_id", caller.getId()),
                Criteria.where("status").is("pending"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRequester_id()).orElse(null),
                        rel.getStatus()))
                .filter(r -> r != null)
                .toList();
    }

    public List<FollowUserResponse> getSentFollowRequests(String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", caller.getId()),
                Criteria.where("status").is("pending"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

        return mongoTemplate.find(query, Relationship.class).stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRecipient_id()).orElse(null),
                        rel.getStatus()))
                .filter(r -> r != null)
                .toList();
    }

    public void cancelSentFollowRequest(String recipientId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("User not found: " + recipientId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), recipientId),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new RuntimeException("Pending follow request not found");
        }

        relationshipRepository.deleteById(relationship.getId());
    }

    public FollowUserResponse acceptFollowRequest(String requesterId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("User not found: " + requesterId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(requester.getId(), caller.getId()),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new RuntimeException("Follow request not found");
        }

        relationship.setStatus("accepted");
        relationship.setUpdated_at(Instant.now());
        relationshipRepository.save(relationship);

        return toFollowUserResponse(requester, "accepted");
    }

    public void rejectFollowRequest(String requesterId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("User not found: " + requesterId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(requesterId, caller.getId()),
                Relationship.class);

        if (relationship == null || !"pending".equalsIgnoreCase(relationship.getStatus())) {
            throw new RuntimeException("Follow request not found");
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
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUserId));

        Relationship relationship = mongoTemplate.findOne(
                relationshipQuery(caller.getId(), target.getId()),
                Relationship.class);

        if (relationship == null || !"accepted".equalsIgnoreCase(relationship.getStatus())) {
            throw new RuntimeException("Relationship not found");
        }

        relationship.setType(isCloseFriend ? "close_friend" : "follow");
        relationship.setFriendship_level(isCloseFriend ? "close_friend" : "normal");
        relationship.setUpdated_at(Instant.now());
        relationshipRepository.save(relationship);

        return toFollowUserResponse(target, relationship.getStatus());
    }

    public List<FollowUserResponse> getCloseFriends(String userId, String callerPrincipal) {
        User caller = resolveUserFromPrincipal(callerPrincipal);
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Query query = new Query(new Criteria().andOperator(
                idCriteria("requester_id", userId),
                Criteria.where("status").is("accepted"),
                Criteria.where("type").is("close_friend"))).with(Sort.by(Sort.Direction.DESC, "updated_at"));

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
            throw new RuntimeException("Invalid user principal");
        }
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .or(() -> userRepository.findById(principal))
                .orElseThrow(() -> new RuntimeException("User not found: " + principal));
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
            throw new RuntimeException("is_private is required");
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
}
