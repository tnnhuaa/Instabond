package com.instabond.dto;

import com.instabond.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaggedUserDTO {
    private String id;
    private String username;
    private String full_name;
    private String avatar_url;
    private Boolean is_private;
    private Double confidence;
    private Post.TaggedUser.Position position;
}
