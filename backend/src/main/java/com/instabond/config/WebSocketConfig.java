package com.instabond.config;

import com.instabond.security.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures WebSocket messaging with STOMP protocol.
 * Note for mobile team:
 * - Endpoint: ws://<host>/ws
 * - Send messages to server: prefix with /app (e.g. /app/chat.send)
 * - Listen for messages from server: subscribe to /user/queue/events
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // For Mobile, Mobile connect to ws://domain/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // TODO: Replace "*" with domain/app scheme of your mobile clients in production

        // Fallback SockJS, Web connect to ws://domain/ws-web
        registry.addEndpoint("/ws-web")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client messages with destination starting with "/app" will be routed to @MessageMapping handlers
        registry.setApplicationDestinationPrefixes("/app");

        // Open a simple in-memory message broker for destinations starting with "/topic" (group) and "/user" (private)
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler());

        // Prefix for user-specific messages (e.g. private chat)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add our JWT interceptor to authenticate WebSocket CONNECT frames
        registration.interceptors(jwtChannelInterceptor);
    }
}
