package com.instabond.service;

import com.instabond.entity.Conversation;
import com.instabond.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

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
}
