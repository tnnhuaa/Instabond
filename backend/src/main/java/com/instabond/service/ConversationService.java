package com.instabond.service;

import com.instabond.dto.ConversationDTO;
import com.instabond.dto.ConversationPageResponse;
import com.instabond.entity.Conversation;
import com.instabond.entity.User;
import com.instabond.exception.ResourceNotFoundException;
import com.instabond.repository.ConversationRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final UserRepository userRepository;

    private final MongoTemplate mongoTemplate;

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

        // Batch query to get intimacy scores for all participants in this page
        Map<String, Integer> scoreMap = getIntimacyScoresBatch(userId, participantIds);

        // Query database to get user details
        Map<String, User> userMap = userRepository.findAllById(participantIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Mapping Conversation to ConversationDTO with participant usernames
        List<ConversationDTO> dtoData = data.stream()
                .map(conv -> toConversationDTO(conv, userMap, userId, scoreMap))
                .toList();

        return ConversationPageResponse.builder()
                .data(dtoData)
                .next_cursor(nextCursor)
                .has_more(hasMore)
                .limit(safeLimit)
                .build();
    }

    private ConversationDTO toConversationDTO(Conversation conversation, Map<String, User> userMap, String callerId, Map<String, Integer> scoreMap) {
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
                        dto.setAvatar_url(u != null ? u.getAvatar_url() : "");
                        int score = pId.equals(callerId) ? 0 : scoreMap.getOrDefault(pId, 0);
                        dto.setIntimacy_score(score);
                        return dto;
                    })
                    .collect(Collectors.toList());
        }

        return ConversationDTO.builder()
                .id(conversation.getId())
                .participants(participantDTOs)
                .last_message(lastMessageDTO)
                .theme(conversation.getTheme())
                .updated_at(conversation.getUpdated_at())
                .build();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // BATCH QUERY HELPER
    private Map<String, Integer> getIntimacyScoresBatch(String callerId, Set<String> targetIds) {
        Map<String, Integer> scoreMap = new HashMap<>();
        if (callerId == null || targetIds == null || targetIds.isEmpty()) return scoreMap;

        Set<String> others = targetIds.stream()
                .filter(id -> id != null && !id.equals(callerId))
                .collect(Collectors.toSet());

        if (others.isEmpty()) return scoreMap;

        Criteria c1 = new Criteria().andOperator(
                idCriteria("requester_id", callerId),
                Criteria.where("recipient_id").in(others)
        );
        Criteria c2 = new Criteria().andOperator(
                Criteria.where("requester_id").in(others),
                idCriteria("recipient_id", callerId)
        );

        Query relQuery = new Query(new Criteria().orOperator(c1, c2));
        List<Map> rels = mongoTemplate.find(relQuery, Map.class, "relationships");

        for (Map rel : rels) {
            String reqId = rel.get("requester_id").toString();
            String recId = rel.get("recipient_id").toString();
            String otherId = reqId.equals(callerId) ? recId : reqId;

            if (rel.get("intimacy_score") != null) {
                scoreMap.put(otherId, ((Number) rel.get("intimacy_score")).intValue());
            }
        }
        return scoreMap;
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
}
