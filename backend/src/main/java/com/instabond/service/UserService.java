package com.instabond.service;

import com.instabond.dto.UserMeResponse;
import com.instabond.entity.User;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserMeResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserMeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .full_name(user.getFull_name())
                .avatar_url(user.getAvatar_url())
                .bio(user.getBio())
                .created_at(user.getCreated_at())
                .build();
    }
}

