package com.instabond.service;

import com.instabond.dto.ConversationDTO;
import com.instabond.dto.ConversationPageResponse;
import com.instabond.entity.Conversation;
import com.instabond.entity.Relationship;
import com.instabond.entity.User;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.ConversationRepository;
import com.instabond.repository.RelationshipRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ConversationRepository conversationRepository;
    private final RelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    public Conversation getOrCreateDirectConversation(String currentUserId, String partnerId) {
        if (currentUserId.equals(partnerId)) {
            throw new IllegalArgumentException("Cannot create a conversation with oneself");
        }

        return conversationRepository.findDirectConversation(currentUserId, partnerId)
                .orElseGet(() -> {
                    Conversation newConversation = Conversation.builder()
                            .participants(List.of(currentUserId, partnerId))
                            .theme("default")
                            .updated_at(Instant.now())
                            // last_message is null for new conversation
                            .build();
                    return conversationRepository.save(newConversation);
                });
    }

    public ConversationDTO getOrCreateDirectConversationDto(String currentUserId, String partnerId) {
        Conversation conversation = getOrCreateDirectConversation(currentUserId, partnerId);
        Map<String, User> userMap = new HashMap<>();
        if (conversation.getParticipants() != null && !conversation.getParticipants().isEmpty()) {
            userRepository.findAllById(conversation.getParticipants())
                    .forEach(user -> userMap.put(user.getId(), user));
        }
        Map<String, Relationship.Streak> streakMap = buildStreakMap(currentUserId, Set.of(partnerId));
        return toConversationDTO(conversation, userMap, currentUserId, streakMap);
    }

    public List<String> getParticipantEmail(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        List<String> participantIds = conversation.getParticipants();

        if (participantIds == null || participantIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(participantIds)
                .stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
    }

    public ConversationPageResponse getUserConversations(String userId, Instant cursor, int limit) {
        int safeLimit = normalizeLimit(limit);
        Pageable pageable = PageRequest.of(0, safeLimit + 1, Sort.by(Sort.Direction.DESC, "updated_at"));

        List<Conversation> rawResult = cursor == null
                ? conversationRepository.findConversationsForUser(userId, pageable)
                : conversationRepository.findConversationsForUserWithCursor(userId, cursor, pageable);

        boolean hasMore = rawResult.size() > safeLimit;
        List<Conversation> data = hasMore
                ? new ArrayList<>(rawResult.subList(0, safeLimit))
                : rawResult;

        Instant nextCursor = hasMore && !data.isEmpty()
                ? data.get(data.size() - 1).getUpdated_at()
                : null;

        // Fetch all unique participant IDs from the conversations in this page
        Set<String> participantIds = new HashSet<>();
        for (Conversation conv : data) {
            if (conv.getParticipants() != null) {
                participantIds.addAll(conv.getParticipants());
            }
        }

        // Query database to get user details
        Map<String, User> userMap = userRepository.findAllById(participantIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<String> partnerIds = participantIds.stream()
                .filter(id -> id != null && !id.equals(userId))
                .collect(Collectors.toSet());
        Map<String, Relationship.Streak> streakMap = buildStreakMap(userId, partnerIds);

        // Mapping Conversation to ConversationDTO with participant usernames
        List<ConversationDTO> dtoData = data.stream()
                .map(conv -> toConversationDTO(conv, userMap, userId, streakMap))
                .toList();

        return ConversationPageResponse.builder()
                .data(dtoData)
                .next_cursor(nextCursor)
                .has_more(hasMore)
                .limit(safeLimit)
                .build();
    }

    private ConversationDTO toConversationDTO(
            Conversation conversation,
            Map<String, User> userMap,
            String currentUserId,
            Map<String, Relationship.Streak> streakMap
    ) {
        if (conversation == null) {
            return null;
        }

        Conversation.LastMessage lastMessage = conversation.getLast_message();
        ConversationDTO.LastMessageDTO lastMessageDTO = lastMessage == null
                ? null
                : ConversationDTO.LastMessageDTO.builder()
                .content(lastMessage.getContent())
                .sender_id(lastMessage.getSender_id())
                .sent_at(lastMessage.getSent_at())
                .is_read(lastMessage.is_read())
                .build();

        List<ConversationDTO.ParticipantDTO> participantDTOs = new ArrayList<>();
        if (conversation.getParticipants() != null) {
            participantDTOs = conversation.getParticipants().stream()
                    .map(pId -> {
                        User u = userMap.get(pId);
                        ConversationDTO.ParticipantDTO dto = new ConversationDTO.ParticipantDTO();
                        dto.setId(pId);

                        dto.setUsername(u != null && u.getUsername() != null ? u.getUsername() : "Unknown User");
                        dto.setEmail(u != null ? u.getEmail() : "");
                        dto.setAvatar_url(u != null ? u.getAvatar_url() : "");
                        if (currentUserId != null && !currentUserId.equals(pId)) {
                            Relationship.Streak streak = streakMap.get(pId);
                            dto.setStreak_count(streak != null ? streak.getCount() : 0);
                            dto.setHas_fired_streak(streak != null && streak.isHas_fired_streak());
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());
        }

        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .participants(participantDTOs)
                .last_message(lastMessageDTO)
                .theme(conversation.getTheme())
                .updated_at(conversation.getUpdated_at())
                .build();
    }

    private Map<String, Relationship.Streak> buildStreakMap(String requesterId, Set<String> recipientIds) {
        if (requesterId == null || recipientIds == null || recipientIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> cleanedRecipientIds = recipientIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(requesterId))
                .distinct()
                .toList();

        if (cleanedRecipientIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return relationshipRepository.findByRequesterIdAndRecipientIdIn(requesterId, cleanedRecipientIds)
                .stream()
                .filter(relationship -> relationship.getRecipient_id() != null)
                .collect(Collectors.toMap(
                        Relationship::getRecipient_id,
                        Relationship::getStreak,
                        (left, right) -> left
                ));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
