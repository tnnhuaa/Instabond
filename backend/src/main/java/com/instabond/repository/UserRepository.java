package com.instabond.repository;

import com.instabond.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    @Query("{ 'qr_code_uid': ?0 }")
    Optional<User> findByQrCodeUid(String qrCodeUid);

    // Logic search
    @Query("{ '$or': [ { 'username': { $regex: ?0, $options: 'i' } }, { 'full_name': { $regex: ?0, $options: 'i' } } ] }")
    List<User> searchUsers(String keyword, Pageable pageable);
}
