package com.example.instabond_fe.utils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DeletedPostRegistry {
    private static final Set<String> DELETED_POST_IDS = new LinkedHashSet<>();
    private static int version = 0;

    private DeletedPostRegistry() {
    }

    public static synchronized void markDeleted(String postId) {
        if (postId == null || postId.trim().isEmpty()) {
            return;
        }

        if (DELETED_POST_IDS.add(postId.trim())) {
            version++;
        }
    }

    public static synchronized Set<String> snapshotDeletedPostIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(DELETED_POST_IDS));
    }

    public static synchronized int getVersion() {
        return version;
    }
}
