package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMeResponse {
    private String id;
    private String username;
    private String email;
    private String full_name;
    private String avatar_url;
    private String bio;
    private Instant created_at;
}

