package com.instabond.repository;

import com.instabond.entity.Post;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {

    @Query(value = "{ 'author_id': ?0 }", sort = "{ 'created_at': -1 }")
    List<Post> findByAuthorIdOrderByCreatedAtDesc(ObjectId authorId);

    @Query(value = "{}", sort = "{ 'created_at': -1 }")
    List<Post> findAllByOrderByCreatedAtDesc();
}
