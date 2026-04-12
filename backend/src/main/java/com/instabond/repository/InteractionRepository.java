package com.instabond.repository;

import com.instabond.entity.Interaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InteractionRepository extends MongoRepository<Interaction, String> {

    @Query("{ 'user_id': ?0, 'target_id': ?1, 'target_type': ?2, 'type': ?3 }")
    Optional<Interaction> findOne(String userId, String targetId, String targetType, String type);

    @Query(value = "{ 'target_id': ?0, 'target_type': ?1, 'type': ?2 }", sort = "{ 'created_at': -1 }")
    List<Interaction> findByTargetAndType(String targetId, String targetType, String type);
}

