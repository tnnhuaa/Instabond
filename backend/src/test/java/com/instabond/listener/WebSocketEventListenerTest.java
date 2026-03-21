package com.instabond.listener;

import com.instabond.service.PresenceService;
import com.instabond.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Collections;

import static org.mockito.Mockito.*;

class WebSocketEventListenerTest {

    @Test
    void marksUserOnlineWhenConnectEventHasPrincipal() {
        UserService userService = mock(UserService.class);
        PresenceService presenceService = mock(PresenceService.class);
        WebSocketEventListener listener = new WebSocketEventListener(userService, presenceService);

        Principal principal = () -> "john@example.com";
        Message<byte[]> message = mock(Message.class);
        when(message.getHeaders()).thenReturn(new MessageHeaders(Collections.emptyMap()));
        SessionConnectedEvent event = new SessionConnectedEvent(this, message, principal);

        listener.handleWebSocketConnectListener(event);

        verify(presenceService).markOnline("john@example.com");
        verifyNoInteractions(userService);
    }

    @Test
    void marksUserOfflineAndUpdatesLastActiveOnDisconnect() {
        UserService userService = mock(UserService.class);
        PresenceService presenceService = mock(PresenceService.class);
        WebSocketEventListener listener = new WebSocketEventListener(userService, presenceService);

        Principal principal = () -> "john@example.com";
        Message<byte[]> message = mock(Message.class);
        when(message.getHeaders()).thenReturn(new MessageHeaders(Collections.emptyMap()));

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "s-1", CloseStatus.NORMAL, principal);

        listener.handleWebSocketDisconnectListener(event);

        verify(presenceService).markOffline("john@example.com");
        verify(userService).updateLastActive(eq("john@example.com"), any());
    }
}
