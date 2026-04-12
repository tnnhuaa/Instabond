package com.instabond.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.presence", name = "redis-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPresenceService implements PresenceService {

    @PostConstruct
    void logMode() {
        log.warn("PresenceService is running in NO-OP mode. Set app.presence.redis-enabled=true to enable Redis-backed presence.");
    }

    @Override
    public void markOnline(String email) {
        // no-op when Redis presence is disabled
    }

    @Override
    public void markOffline(String email) {
        // no-op when Redis presence is disabled
    }

    @Override
    public boolean isOnline(String email) {
        return false;
    }
}
