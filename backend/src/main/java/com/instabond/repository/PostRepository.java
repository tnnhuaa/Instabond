package com.instabond.repository;

import com.instabond.entity.Post;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
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

    // Logic search
    @Query("{ '$or': [ { 'caption': { $regex: ?0, $options: 'i' } }, { 'location.name': { $regex: ?0, $options: 'i' } } ] }")
    List<Post> searchPosts(String keyword, Pageable pageable);

    @Query(value = "{}", fields = "{ '_id': 1 }")
    List<PostIdProjection> findAllPostIds();

    List<Post> findByIdIn(List<String> ids);

    interface PostIdProjection {
        String getId();
    }
}
