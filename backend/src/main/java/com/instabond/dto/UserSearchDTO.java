package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchDTO {
    private String id;
    private String username;
    private String full_name;
    private String avatar_url;
    private Boolean is_private;
}
