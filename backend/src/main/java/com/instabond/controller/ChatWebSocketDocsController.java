package com.instabond.controller;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
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
            summary = "SUBSCRIBE: Listen for new messages (Server -> Client)",
            description = "**Protocol:** STOMP WebSocket\n\n**Topic:** `/topic/conversations/{conversationId}`\n\n*Note: This documents the payload received when a new text/image message arrives.*"
    )
    @GetMapping("/topic/conversations/{conversationId}")
    public ChatMessageResponse documentReceiveMessage(@PathVariable String conversationId) {
        throw new UnsupportedOperationException("Virtual endpoint for Swagger docs only.");
    }
}
