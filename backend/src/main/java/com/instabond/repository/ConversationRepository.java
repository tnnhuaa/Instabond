package com.instabond.repository;

import com.instabond.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // Find a conversation of two participants (for 1-on-1 chat)
    @Query("{ 'participants': { $all: [?0, ?1], $size: 2 } }")
    Optional<Conversation> findDirectConversation(String userId1, String userId2);
}
