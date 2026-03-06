package com.instabond.repository;

import com.instabond.entity.Relationship;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRepository extends MongoRepository<Relationship, String> {

    @Query("{ 'recipient_id': ?0, 'status': ?1 }")
    List<Relationship> findByRecipientIdAndStatus(ObjectId recipientId, String status);

    @Query("{ 'requester_id': ?0, 'status': ?1 }")
    List<Relationship> findByRequesterIdAndStatus(ObjectId requesterId, String status);

    @Query(value = "{ 'recipient_id': ?0, 'status': ?1 }", count = true)
    long countByRecipientIdAndStatus(ObjectId recipientId, String status);

    @Query(value = "{ 'requester_id': ?0, 'status': ?1 }", count = true)
    long countByRequesterIdAndStatus(ObjectId requesterId, String status);

    @Query("{ 'requester_id': ?0, 'recipient_id': ?1 }")
    Optional<Relationship> findByRequesterIdAndRecipientId(ObjectId requesterId, ObjectId recipientId);
}

