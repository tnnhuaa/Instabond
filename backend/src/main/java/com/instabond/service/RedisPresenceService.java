package com.instabond.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.presence", name = "redis-enabled", havingValue = "true")
public class RedisPresenceService implements PresenceService {

    private static final String KEY_PREFIX = "USER_ONLINE:";
    private static final long WARN_THROTTLE_MS = 30_000;

    private final StringRedisTemplate redisTemplate;
    private final AtomicLong lastWarnAt = new AtomicLong(0);
    private final AtomicLong retryAfterMs = new AtomicLong(0);

    @Value("${app.presence.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${app.presence.retry-backoff-seconds:30}")
    private long retryBackoffSeconds;

    @Override
    public void markOnline(String email) {
        if (!canAttemptRedis()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + email, "true", Duration.ofMinutes(ttlMinutes));
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure("mark online", email, ex);
        }
    }

    @Override
    public void markOffline(String email) {
        if (!canAttemptRedis()) {
            return;
        }

        try {
            redisTemplate.delete(KEY_PREFIX + email);
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure("mark offline", email, ex);
        }
    }

    @Override
    public boolean isOnline(String email) {
        if (!canAttemptRedis()) {
            return false;
        }

        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + email);
            return Boolean.TRUE.equals(exists);
        } catch (RedisConnectionFailureException ex) {
            onRedisFailure("check online status", email, ex);
            return false;
        }
    }

    private boolean canAttemptRedis() {
        return System.currentTimeMillis() >= retryAfterMs.get();
    }

    private void onRedisFailure(String action, String email, RedisConnectionFailureException ex) {
        long now = System.currentTimeMillis();
        retryAfterMs.set(now + (retryBackoffSeconds * 1000));

        long previous = lastWarnAt.get();
        if (now - previous >= WARN_THROTTLE_MS && lastWarnAt.compareAndSet(previous, now)) {
            log.warn("Redis unavailable, cannot {} for user {}. Presence degraded: {}", action, email, ex.getMessage());
            return;
        }

        log.debug("Redis unavailable while trying to {} for user {}", action, email);
    }
}

