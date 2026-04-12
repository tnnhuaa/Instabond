package com.instabond.listener;

import com.instabond.dto.WsEvent;
import com.instabond.service.PresenceService;
import com.instabond.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private static final String EVENTS_DESTINATION = "/queue/events";

    private final UserService userService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Set<String> connectedUsers = ConcurrentHashMap.newKeySet();

    /**
     * Listen event: CONNECT (User is Online)
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal userPrincipal = event.getUser();

        if (userPrincipal != null) {
            String email = userPrincipal.getName();
            String userId = userService.getUserIdByEmail(email);
            connectedUsers.add(email);
            presenceService.markOnline(email);

            log.info("User {} connected to WebSocket", email);

            broadcastPresenceEvent(
                    Map.of(
                            "email", email,
                            "userId", userId,
                            "online", true
                    )
            );
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
            String userId = userService.getUserIdByEmail(email);
            connectedUsers.remove(email);
            presenceService.markOffline(email);

            log.info("User {} disconnected from WebSocket", email);

            // Get session ID for debugging
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            log.debug("Session ID is disconnected: {}", sessionId);

            Instant now = Instant.now();
            userService.updateLastActive(email, now);

            broadcastPresenceEvent(
                    Map.of(
                            "email", email,
                            "userId", userId,
                            "online", false,
                            "lastActive", now.toEpochMilli()
                    )
            );
        }
    }

    private void broadcastPresenceEvent(Map<String, Object> payload) {
        WsEvent<Map<String, Object>> presenceEvent = WsEvent.of(WsEvent.TYPE_PRESENCE, payload);

        for (String connectedEmail : connectedUsers) {
            messagingTemplate.convertAndSendToUser(
                    connectedEmail,
                    EVENTS_DESTINATION,
                    presenceEvent
            );
        }
    }
}
