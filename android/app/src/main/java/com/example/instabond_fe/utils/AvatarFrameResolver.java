package com.example.instabond_fe.utils;

import androidx.annotation.DrawableRes;

import com.example.instabond_fe.R;

public final class AvatarFrameResolver {

    private AvatarFrameResolver() {
    }

    public static int resolveInboxActiveRing(boolean isLeadingItem) {
        return isLeadingItem
                ? R.drawable.comment_author_ring_gradient
                : R.drawable.bg_active_avatar_ring_neutral;
    }

    public static StoryFeedFrame resolveStoryFeedFrame(boolean isCreateCard, boolean hasActiveStory) {
        if (isCreateCard && !hasActiveStory) {
            return new StoryFeedFrame(
                    R.drawable.feed_story_add_bg,
                    android.R.color.transparent,
                    0
            );
        }

        return new StoryFeedFrame(
                R.drawable.feed_story_active_ring,
                R.drawable.feed_story_inner_frame_bg,
                2
        );
    }

    public static AvatarFrame resolveSettingsAvatarFrame() {
        return new AvatarFrame(R.drawable.settings_avatar_ring, R.drawable.profile_placeholder_bg, 3);
    }

    public static AvatarFrame resolveStoryViewerAvatarFrame() {
        return new AvatarFrame(R.drawable.story_viewer_send_button_bg, R.drawable.avatar_circle_bg, 2);
    }

    public static AvatarFrame resolveCreateStoryComposerAvatarFrame() {
        return new AvatarFrame(R.drawable.story_create_avatar_ring, R.drawable.avatar_circle_bg, 2);
    }

    public static AvatarFrame resolveChatHeaderAvatarFrame() {
        return new AvatarFrame(R.drawable.comment_author_ring_gradient, R.drawable.avatar_circle_bg, 2);
    }

    public static AvatarFrame resolvePostAvatarFrame(int intimacyScore) {
        @DrawableRes int ringDrawable;

        if (intimacyScore >= 1000) {
            ringDrawable = R.drawable.avatar_ring_gradient_red;
        } else if (intimacyScore >= 300) {
            ringDrawable = R.drawable.avatar_ring_gradient_yellow;
        } else if (intimacyScore >= 100) {
            ringDrawable = R.drawable.avatar_ring_gradient_blue;
        } else {
            ringDrawable = android.R.color.transparent;
        }

        // Trả về đúng object AvatarFrame (Ring background, Avatar background, Padding = 2dp)
        return new AvatarFrame(ringDrawable, R.drawable.avatar_circle_bg, 2);
    }

    @DrawableRes
    public static int resolveCommentComposerAvatarBackground() {
        return R.drawable.avatar_circle_bg;
    }

    @DrawableRes
    public static int resolveCreatePostAvatarBackground() {
        return R.drawable.avatar_circle_bg;
    }

    public static final class StoryFeedFrame {
        @DrawableRes
        public final int ringBackgroundRes;
        @DrawableRes
        public final int innerBackgroundRes;
        public final int innerPaddingDp;

        public StoryFeedFrame(@DrawableRes int ringBackgroundRes, @DrawableRes int innerBackgroundRes, int innerPaddingDp) {
            this.ringBackgroundRes = ringBackgroundRes;
            this.innerBackgroundRes = innerBackgroundRes;
            this.innerPaddingDp = innerPaddingDp;
        }
    }

    public static final class AvatarFrame {
        @DrawableRes
        public final int ringBackgroundRes;
        @DrawableRes
        public final int avatarBackgroundRes;
        public final int ringPaddingDp;

        public AvatarFrame(@DrawableRes int ringBackgroundRes, @DrawableRes int avatarBackgroundRes, int ringPaddingDp) {
            this.ringBackgroundRes = ringBackgroundRes;
            this.avatarBackgroundRes = avatarBackgroundRes;
            this.ringPaddingDp = ringPaddingDp;
        }
    }
}
