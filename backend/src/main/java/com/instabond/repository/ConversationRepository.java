package com.instabond.repository;

import com.instabond.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // Find a conversation of two participants (for 1-on-1 chat)
    @Query("{ 'participants': { $all: [?0, ?1], $size: 2 } }")
    Optional<Conversation> findDirectConversation(String userId1, String userId2);

    // Fetch latest conversations for a participant.
    @Query("{ 'participants': ?0 }")
    List<Conversation> findConversationsForUser(String userId, Pageable pageable);

    // Fetch conversations older than cursor (updated_at < cursor) for load-more.
    @Query("{ 'participants': ?0, 'updated_at': { $lt: ?1 } }")
    List<Conversation> findConversationsForUserWithCursor(String userId, Instant cursor, Pageable pageable);
}
