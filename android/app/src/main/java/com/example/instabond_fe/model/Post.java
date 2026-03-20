package com.example.instabond_fe.model;

import java.util.ArrayList;
import java.util.List;

public class Post {
    private final String id;
    private final String authorId;
    private final String username;
    private final String caption;
    private int likesCount;
    private final int commentsCount;
    private final int sharesCount;
    private final String avatarUrl;
    private final String imageUrl;
    private final boolean hasMusicBadge;
    private boolean isLiked;

    public Post(String id, String authorId, String username, String caption, int likesCount, int commentsCount, int sharesCount,
                String avatarUrl, String imageUrl, boolean hasMusicBadge, boolean isLiked) {
        this.id = id;
        this.authorId = authorId;
        this.username = username;
        this.caption = caption;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.sharesCount = sharesCount;
        this.avatarUrl = avatarUrl;
        this.imageUrl = imageUrl;
        this.hasMusicBadge = hasMusicBadge;
        this.isLiked = isLiked;
    }

    public String getId() {
        return id;
    }

    public String getAuthorId() {
        return authorId;
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

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public int getSharesCount() {
        return sharesCount;
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

    public boolean isLiked() {
        return isLiked;
    }

    public void setLiked(boolean liked) {
        isLiked = liked;
    }

    /** Returns a list of mock posts for the Newsfeed */
    public static List<Post> getMockPosts() {
        List<Post> posts = new ArrayList<>();
        posts.add(new Post("1", "u1", "minh_anh", "Sáng nay thật tuyệt ☕️✨", 234, 12, 5, "", "", true, false));
        posts.add(new Post("2", "u2", "tuan_kiet", "Hoàng hôn đẹp quá 🌅", 512, 34, 11, "", "", false, false));
        posts.add(new Post("3", "u3", "thu_hang", "Cuối tuần đi dạo phố cổ 🏮", 189, 8, 3, "", "", true, false));
        posts.add(new Post("4", "u4", "quoc_bao", "Bữa trưa ngon miệng 🍜", 97, 5, 1, "", "", false, false));
        posts.add(new Post("5", "u5", "lan_anh", "Khoảnh khắc bình yên 🌿", 341, 21, 9, "", "", true, false));
        posts.add(new Post("6", "u6", "duc_thinh", "Đêm Sài Gòn sáng rực 🌃", 678, 45, 17, "", "", false, false));
        return posts;
    }
}
