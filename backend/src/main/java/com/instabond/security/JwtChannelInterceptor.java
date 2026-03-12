package com.instabond.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocket (STOMP) Connection Security Layer

 * * Note for mobile team:
    * - The client MUST include a token when initiating a connection (CONNECT command)
    * - Header format: Authorization: Bearer <jwt-token>
    * - If the token is missing, invalid, or expired, the server will immediately reject the connection
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only authenticate on CONNECT frames
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    String email = jwtUtil.extractEmail(jwt);

                    if (email != null && jwtUtil.isTokenValid(jwt, email)) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        // Attach the "Authenticated" principal to the STOMP session
                        // Controllers (@MessageMapping) can access user's information via @AuthenticationPrincipal
                        accessor.setUser(authentication);
                        log.debug("WebSocket CONNECT authenticated for user: {}", email);
                    } else {
                        log.warn("WebSocket CONNECT rejected: invalid or expired token");
                        throw new IllegalArgumentException("Invalid or expired JWT token");
                    }
                } catch (Exception e) {
                    log.warn("WebSocket CONNECT rejected: {}", e.getMessage());
                    throw new IllegalArgumentException("WebSocket authentication failed: " + e.getMessage(), e);
                }
            } else {
                log.warn("WebSocket CONNECT rejected: missing or malformed Authorization header");
                throw new IllegalArgumentException("Authorization header is missing or malformed");
            }
        }

        return message;
    }
}
