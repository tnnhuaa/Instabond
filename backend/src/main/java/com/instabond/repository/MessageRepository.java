package com.instabond.repository;

import com.instabond.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    @Query(value = "{ 'conversation_id': ?0 }", sort = "{ 'created_at': 1 }")
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    @Query(value = "{ 'conversation_id': ?0 }", sort = "{ 'created_at': 1 }")
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);
}
