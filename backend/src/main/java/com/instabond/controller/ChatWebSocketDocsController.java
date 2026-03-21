package com.instabond.controller;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.dto.WsEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * Fake REST Controller exclusively for generating Swagger UI documentation
 * for WebSocket endpoints.
 */
@RestController
@RequestMapping("/api/docs/websocket")
@Tag(
        name = "WebSocket Chat API",
        description = "FAKE ENDPOINTS FOR DOCUMENTATION ONLY. DO NOT click 'Try it out'. You cannot test WebSocket connections through this Swagger UI.")
public class ChatWebSocketDocsController {

    @Operation(
            summary = "SEND: Send a text message (Client -> Server)",
            description = "**Protocol:** STOMP WebSocket\n\n**Destination:** `/app/chat.send`\n\n*Note: This is a virtual endpoint to expose the request body for the Mobile team.*"
    )
    @PostMapping("/app/chat.send")
    public void documentSendMessage(@RequestBody ChatMessageRequest request) {
        throw new UnsupportedOperationException("Virtual endpoint for Swagger docs only.");
    }

    @Operation(
            summary = "SUBSCRIBE: Listen for websocket events (Server -> Client)",
            description = "**Protocol:** STOMP WebSocket\n\n**Destination:** `/user/queue/events`\n\n*Note: Payload is wrapped as `WsEvent<T>` where type can be CHAT, NOTIFICATION, PRESENCE or ERROR.*"
    )
    @GetMapping("/user/queue/events")
    public WsEvent<ChatMessageResponse> documentReceiveMessage() {
        throw new UnsupportedOperationException("Virtual endpoint for Swagger docs only.");
    }
}
