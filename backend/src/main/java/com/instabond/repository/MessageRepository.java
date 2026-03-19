package com.instabond.repository;

import com.instabond.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query(value = "{ 'conversation_id': ?0 }", sort = "{ 'created_at': 1 }")
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    @Query(value = "{ 'conversation_id': ?0 }", sort = "{ 'created_at': 1 }")
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);

    @Query(value = "{ 'conversation_id': ?0, 'sender_id': { $ne: ?1 }, 'read_by.user_id': { $ne: ?1 } }")
    List<Message> findUnreadMessages(String conversationId, String readerId);
}
