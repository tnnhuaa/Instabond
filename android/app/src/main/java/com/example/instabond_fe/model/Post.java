package com.example.instabond_fe.model;

import java.util.ArrayList;
import java.util.List;

public class Post {
    private final String username;
    private final String caption;
    private final int likesCount;
    private final int commentsCount;
    private final String avatarUrl;
    private final String imageUrl;
    private final boolean hasMusicBadge;

    public Post(String username, String caption, int likesCount, int commentsCount,
                String avatarUrl, String imageUrl, boolean hasMusicBadge) {
        this.username = username;
        this.caption = caption;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.avatarUrl = avatarUrl;
        this.imageUrl = imageUrl;
        this.hasMusicBadge = hasMusicBadge;
    }

    public String getUsername() {
        return username;
    }

    public String getCaption() {
        return caption;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isHasMusicBadge() {
        return hasMusicBadge;
    }

    /** Returns a list of mock posts for the Newsfeed */
    public static List<Post> getMockPosts() {
        List<Post> posts = new ArrayList<>();
        posts.add(new Post("minh_anh", "Sáng nay thật tuyệt ☕️✨", 234, 12, "", "", true));
        posts.add(new Post("tuan_kiet", "Hoàng hôn đẹp quá 🌅", 512, 34, "", "", false));
        posts.add(new Post("thu_hang", "Cuối tuần đi dạo phố cổ 🏮", 189, 8, "", "", true));
        posts.add(new Post("quoc_bao", "Bữa trưa ngon miệng 🍜", 97, 5, "", "", false));
        posts.add(new Post("lan_anh", "Khoảnh khắc bình yên 🌿", 341, 21, "", "", true));
        posts.add(new Post("duc_thinh", "Đêm Sài Gòn sáng rực 🌃", 678, 45, "", "", false));
        return posts;
    }
}
