package com.instabond.listener;

import com.instabond.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Listen event: CONNECT (User is Online)
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal userPrincipal = event.getUser();

        if (userPrincipal != null) {
            String email = userPrincipal.getName();

            // Save to redis with TTL = 5 minutes
            redisTemplate.opsForValue().set("USER_ONLINE:" + email, "true", Duration.ofMinutes(5));

            log.info("User {} connected to WebSocket", email);

            // @TODO: SimpMessagingTemplate can also be used here to notify friends of this user (Green dot online status)
        }
    }

    /**
     * Listen event: DISCONNECT (User is Offline)
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal userPrincipal = event.getUser();

        if (userPrincipal != null) {
            String email = userPrincipal.getName();

            // Remove from redis
            redisTemplate.delete("USER_ONLINE:" + email);

            log.info("User {} disconnected from WebSocket", email);

            // Get session ID for debugging
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            log.debug("Session ID is disconnected: {}", sessionId);

            userService.updateLastActive(email, Instant.now());
        }
    }
}
