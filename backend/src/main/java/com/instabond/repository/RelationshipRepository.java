package com.instabond.repository;

import com.instabond.entity.Relationship;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRepository extends MongoRepository<Relationship, String> {

    @Query("{ 'recipient_id': ?0, 'status': ?1 }")
    List<Relationship> findByRecipientIdAndStatus(String recipientId, String status);

    @Query("{ 'requester_id': ?0, 'status': ?1 }")
    List<Relationship> findByRequesterIdAndStatus(String requesterId, String status);

    @Query(value = "{ 'recipient_id': ?0, 'status': ?1 }", count = true)
    long countByRecipientIdAndStatus(String recipientId, String status);

    @Query(value = "{ 'requester_id': ?0, 'status': ?1 }", count = true)
    long countByRequesterIdAndStatus(String requesterId, String status);

    @Query("{ 'requester_id': ?0, 'recipient_id': ?1 }")
    Optional<Relationship> findByRequesterIdAndRecipientId(String requesterId, String recipientId);

    @Query("{ 'requester_id': ?0, 'status': ?1, 'type': ?2 }")
    List<Relationship> findByRequesterIdAndStatusAndType(String requesterId, String status, String type);
}
