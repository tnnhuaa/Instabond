package com.instabond.service;

import com.instabond.entity.Conversation;
import com.instabond.entity.User;
import com.instabond.repository.ConversationRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
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

    public List<String> getParticipantUsernames(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        List<String> participantIds = conversation.getParticipants();

        if (participantIds == null || participantIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(participantIds)
                .stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
    }
}
