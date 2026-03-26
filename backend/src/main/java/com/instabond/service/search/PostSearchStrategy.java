package com.instabond.service.search;

import com.instabond.dto.PostSearchDTO;
import com.instabond.entity.Post;
import com.instabond.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostSearchStrategy implements SearchStrategy {
    private final PostRepository postRepository;

    @Override
    public String getType() {
        return "Post";
    }

    @Override
    public List<?> search(String keyword, Pageable pageable) {
        List<Post> posts = postRepository.searchPosts(keyword, pageable);
        return posts.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<?> suggest(String keyword) {
        throw new UnsupportedOperationException("Search suggestions are only supported for USER type");
    }

    private PostSearchDTO mapToDTO(Post post) {
        String thumbnailUrl = null;
        if (post.getMedia() != null && !post.getMedia().isEmpty()) {
            thumbnailUrl = post.getMedia().get(0).getUrl();
        }

        int likes = post.getStats() != null ? post.getStats().getLikes() : 0;
        int comments = post.getStats() != null ? post.getStats().getComments() : 0;

        return PostSearchDTO.builder()
                .id(post.getId())
                .author_id(post.getAuthor_id())
                .caption(post.getCaption())
                .thumbnail_url(thumbnailUrl)
                .likes(likes)
                .comments(comments)
                .build();
    }
}
