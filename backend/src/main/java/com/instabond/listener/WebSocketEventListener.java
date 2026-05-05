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
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private static final String EVENTS_DESTINATION = "/queue/events";
    private static final String USER_EVENTS_DESTINATION = "/user" + EVENTS_DESTINATION;

    private final UserService userService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, AtomicInteger> sessionCounts = new ConcurrentHashMap<>();

    /**
     * Listen event: CONNECT (User is Online)
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal userPrincipal = event.getUser();

        if (userPrincipal != null) {
            String email = userPrincipal.getName();

            int newCount = sessionCounts.computeIfAbsent(email, k -> new AtomicInteger(0)).incrementAndGet();

            log.info("User {} connected to WebSocket (active sessions: {})", email, newCount);

            // Only mark online and broadcast on the FIRST session
            if (newCount == 1) {
                String userId = userService.getUserIdByEmail(email);
                presenceService.markOnline(email);

                broadcastPresenceEvent(
                        Map.of(
                                "email", email,
                                "userId", userId,
                                "online", true
                        )
                );

                sendPresenceSnapshotToUser(email);
            }
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
            AtomicInteger counter = sessionCounts.get(email);

            if (counter == null) {
                log.debug("User {} disconnected but no session counter found (may have been cleaned up)", email);
                return;
            }

            int remaining = counter.decrementAndGet();

            // Get session ID for debugging
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            log.info("User {} session {} disconnected (remaining sessions: {})", email, sessionId, remaining);

            // Only mark offline and broadcast when ALL sessions are gone
            if (remaining <= 0) {
                sessionCounts.remove(email);
                String userId = userService.getUserIdByEmail(email);
                presenceService.markOffline(email);

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
    }

    /**
     * Listen event: SUBSCRIBE (User starts listening for events)
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        Principal userPrincipal = headerAccessor.getUser();

        if (userPrincipal == null || destination == null) {
            return;
        }

        if (!EVENTS_DESTINATION.equals(destination)
            && !USER_EVENTS_DESTINATION.equals(destination)
            && (destination == null || !destination.endsWith(EVENTS_DESTINATION))) {
            return;
        }
        String email = userPrincipal.getName();
        log.info("WebSocket SUBSCRIBE {} for {}, sending presence snapshot", destination, email);
        sendPresenceSnapshotToUser(email);
    }

    private void broadcastPresenceEvent(Map<String, Object> payload) {
        WsEvent<Map<String, Object>> presenceEvent = WsEvent.of(WsEvent.TYPE_PRESENCE, payload);

        for (String connectedEmail : sessionCounts.keySet()) {
            messagingTemplate.convertAndSendToUser(
                    connectedEmail,
                    EVENTS_DESTINATION,
                    presenceEvent
            );
        }
    }

    private void sendPresenceSnapshotToUser(String email) {
        if (email == null) {
            return;
        }

        List<Map<String, Object>> onlineUsers = new ArrayList<>();
        for (String connectedEmail : sessionCounts.keySet()) {
            String userId = userService.getUserIdByEmail(connectedEmail);
            onlineUsers.add(
                    Map.of(
                            "email", connectedEmail,
                            "userId", userId,
                            "online", true
                    )
            );
        }

        if (onlineUsers.isEmpty()) {
            return;
        }

        log.info("Sending presence snapshot to {} (online users: {})", email, onlineUsers.size());
        WsEvent<List<Map<String, Object>>> presenceSnapshot = WsEvent.of(WsEvent.TYPE_PRESENCE, onlineUsers);
        messagingTemplate.convertAndSendToUser(email, EVENTS_DESTINATION, presenceSnapshot);
    }
}
