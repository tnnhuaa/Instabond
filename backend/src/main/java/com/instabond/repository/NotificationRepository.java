package com.instabond.repository;

import com.instabond.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    @Query(value = "{ 'recipient_id': ?0 }", sort = "{ 'created_at': -1 }")
    List<Notification> findByRecipientId(String recipientId, Pageable pageable);

    @Query(value = "{ 'recipient_id': ?0 }", count = true)
    long countByRecipientId(String recipientId);
}

