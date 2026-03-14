package com.instabond.service;

import com.instabond.dto.UserMeResponse;
import com.instabond.dto.FollowUserResponse;
import com.instabond.dto.ProfileResponse;
import com.instabond.dto.UpdateProfileRequest;
import com.instabond.entity.Post;
import com.instabond.entity.Relationship;
import com.instabond.entity.User;
import com.instabond.repository.PostRepository;
import com.instabond.repository.RelationshipRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserMeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .bio(user.getBio())
                .created_at(user.getCreated_at())
                .build();
    }

    // Profile queries

    public List<ProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toProfileResponse)
                .toList();
    }

    public ProfileResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return toProfileResponse(user);
    }

    public ProfileResponse getProfileByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return toProfileResponse(user);
    }

    // Profile updates

    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (request.getFull_name() != null)    user.setFull_name(request.getFull_name());
        if (request.getBio() != null)          user.setBio(request.getBio());
        if (request.getPhone_number() != null) user.setPhone_number(request.getPhone_number());

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

        user.setAvatar_url(fileService.uploadImage(file));
        return toProfileResponse(userRepository.save(user));
    }

    // Social graph

    public List<FollowUserResponse> getFollowers(String userId) {
        ObjectId oid = new ObjectId(userId);
        List<Relationship> rels = relationshipRepository.findByRecipientIdAndStatus(oid, "accepted");
        return rels.stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRequester_id()).orElse(null)))
                .filter(r -> r != null)
                .toList();
    }

    public List<FollowUserResponse> getFollowing(String userId) {
        ObjectId oid = new ObjectId(userId);
        List<Relationship> rels = relationshipRepository.findByRequesterIdAndStatus(oid, "accepted");
        return rels.stream()
                .map(rel -> toFollowUserResponse(userRepository.findById(rel.getRecipient_id()).orElse(null)))
                .filter(r -> r != null)
                .toList();
    }

    // Mappers

    private ProfileResponse toProfileResponse(User user) {
        long postsCount = mongoTemplate.count(
                new Query(Criteria.where("author_id").is(new ObjectId(user.getId()))), Post.class);
        ObjectId oid = new ObjectId(user.getId());
        long followersCount = relationshipRepository.countByRecipientIdAndStatus(oid, "accepted");
        long followingCount = relationshipRepository.countByRequesterIdAndStatus(oid, "accepted");
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
        if (user == null) return null;
        return FollowUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .build();
    }
}
