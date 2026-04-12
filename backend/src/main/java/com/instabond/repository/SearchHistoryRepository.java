package com.instabond.repository;

import com.instabond.dto.SearchHistoryDTO;
import com.instabond.entity.SearchHistory;

import com.instabond.enums.SearchType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {

    Optional<SearchHistory> findByUserIdAndTypeAndKeyword(String userId, SearchType type, String keyword);
    Optional<SearchHistory> findByUserIdAndTypeAndTargetUserId(String userId, SearchType type, String targetUserId);

    void deleteByIdAndUserId(String id, String userId);

    List<SearchHistory> findByUserIdOrderByUpdatedAtAsc(String userId, Pageable pageable);
    List<SearchHistory> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);

    @Aggregation(pipeline = {
            "{ '$match': { 'user_id': ?0 } }",
            "{ '$sort': { 'updated_at': -1 } }",
            "{ '$limit': ?1 }",

            "{ '$addFields': { " +
            "    'target_user_id_obj': { " +
            "        '$cond': { " +
            "            'if': { '$eq': ['$type', 'PROFILE'] }, " +
            "            'then': { '$toObjectId': '$target_user_id' }, " +
            "            'else': null " +
            "        } " +
            "    } " +
            "} }",

            "{ '$lookup': { " +
                    "'from': 'users', " +
                    "'localField': 'target_user_id_obj', " +
                    "'foreignField': '_id', " +
                    "'as': 'target_user' " +
                    "} }",

            "{ '$unwind': { 'path': '$target_user', 'preserveNullAndEmptyArrays': true } }",
            "{ '$project': { " +
                    "'id': { '$toString': '$_id' }, " +
                    "'type': 1, " +
                    "'keyword': 1, " +
                    "'target_user_id': 1, " +
                    "'updated_at': 1, " +
                    "'target_username': '$target_user.username', " +
                    "'target_full_name': '$target_user.full_name', " +
                    "'target_avatar_url': '$target_user.avatar_url' " +
                    "} }"
    })
    List<SearchHistoryDTO> getSearchHistoryWithUserDetails(String userId, int limit);
}