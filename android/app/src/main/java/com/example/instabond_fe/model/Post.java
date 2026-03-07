package com.example.instabond_fe.model;

import java.util.ArrayList;
import java.util.List;

public class Post {
    private String username;
    private String caption;
    private int likesCount;
    private int commentsCount;
    // In a real app these would be URLs; for mock we use 0 (no drawable placeholder)
    private int avatarResId;
    private int imageResId;
    private boolean hasMusicBadge;

    public Post(String username, String caption, int likesCount, int commentsCount,
                int avatarResId, int imageResId, boolean hasMusicBadge) {
        this.username = username;
        this.caption = caption;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.avatarResId = avatarResId;
        this.imageResId = imageResId;
        this.hasMusicBadge = hasMusicBadge;
    }

    public String getUsername() { return username; }
    public String getCaption() { return caption; }
    public int getLikesCount() { return likesCount; }
    public int getCommentsCount() { return commentsCount; }
    public int getAvatarResId() { return avatarResId; }
    public int getImageResId() { return imageResId; }
    public boolean isHasMusicBadge() { return hasMusicBadge; }

    /** Returns a list of mock posts for the Newsfeed */
    public static List<Post> getMockPosts() {
        List<Post> posts = new ArrayList<>();
        posts.add(new Post("minh_anh", "Sáng nay thật tuyệt ☕️✨", 234, 12, 0, 0, true));
        posts.add(new Post("tuan_kiet", "Hoàng hôn đẹp quá 🌅", 512, 34, 0, 0, false));
        posts.add(new Post("thu_hang", "Cuối tuần đi dạo phố cổ 🏮", 189, 8, 0, 0, true));
        posts.add(new Post("quoc_bao", "Bữa trưa ngon miệng 🍜", 97, 5, 0, 0, false));
        posts.add(new Post("lan_anh", "Khoảnh khắc bình yên 🌿", 341, 21, 0, 0, true));
        posts.add(new Post("duc_thinh", "Đêm Sài Gòn sáng rực 🌃", 678, 45, 0, 0, false));
        return posts;
    }
}

