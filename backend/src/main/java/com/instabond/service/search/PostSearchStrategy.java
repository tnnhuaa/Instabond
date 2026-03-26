package com.instabond.service.search;

import com.instabond.dto.PostSearchDTO;
import com.instabond.entity.Post;
import com.instabond.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public List<PostSearchDTO> explore(long seed, Pageable pageable) {
        List<String> shuffledIds = postRepository.findAllPostIds().stream()
                .map(PostRepository.PostIdProjection::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        if (shuffledIds.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(shuffledIds, new Random(seed));

        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= shuffledIds.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + pageable.getPageSize(), shuffledIds.size());
        List<String> pagedIds = shuffledIds.subList(fromIndex, toIndex);

        List<Post> posts = postRepository.findByIdIn(pagedIds);

        Map<String, Integer> orderIndex = IntStream.range(0, pagedIds.size())
                .boxed()
                .collect(Collectors.toMap(pagedIds::get, i -> i));

        posts.sort(Comparator.comparingInt(post -> orderIndex.getOrDefault(post.getId(), Integer.MAX_VALUE)));

        return posts.stream().map(this::mapToDTO).collect(Collectors.toList());
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
