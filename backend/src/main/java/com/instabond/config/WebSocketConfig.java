package com.instabond.config;

import com.instabond.security.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures WebSocket messaging with STOMP protocol.
 * * Note for mobile team:
    * - Endpoint: ws://<host>/ws
    * - Send messages to server : prefix with /app (e.g. /app/chat)
    * - Listen for messages from server : subscribe to /topic/... (group) or /user/queue/... (private)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // TODO: Replace "*" with domain/app scheme of your mobile clients in production
                .withSockJS(); // Fallback to long-polling if WebSocket is not supported
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client messages with destination starting with "/app" will be routed to @MessageMapping handlers
        registry.setApplicationDestinationPrefixes("/app");

        // Open a simple in-memory message broker for destinations starting with "/topic" (group) and "/user" (private)
        registry.enableSimpleBroker("/topic", "/user");

        // Prefix for user-specific messages (e.g. private chat)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add our JWT interceptor to authenticate WebSocket CONNECT frames
        registration.interceptors(jwtChannelInterceptor);
    }
}
