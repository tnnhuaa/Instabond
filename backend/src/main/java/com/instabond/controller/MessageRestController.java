package com.instabond.controller;

import com.instabond.dto.ChatMessageResponse;
import com.instabond.entity.Message;
import com.instabond.service.ConversationService;
import com.instabond.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@NullMarked
@Tag(name = "Chat", description = "REST APIs for chat history, image uploads, and read receipts")
@SecurityRequirement(name = "bearerAuth")
public class MessageRestController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationService conversationService;

    @Operation(
            summary = "Get conversation history",
            description = "Returns paginated messages in ascending time order for the provided conversation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation history returned successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "User is not a participant of this conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @GetMapping("/conversation/{conversationId}/history")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @Parameter(description = "Conversation ID", example = "64f1a2b3c4d5e6f7a8b9c0d2")
            @PathVariable String conversationId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (service enforces a safe upper bound)", example = "30")
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<ChatMessageResponse> history = messageService
                .getConversationHistory(conversationId, userDetails.getUsername(), page, size)
                .stream()
                .map(messageService::toResponse)
                .toList();

        return ResponseEntity.ok(history);
    }

    @Operation(
            summary = "Upload chat image",
            description = "Uploads an image file, stores it as a chat message, then broadcasts it to the conversation topic over WebSocket."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image message created successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "User is not a participant of this conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file or request data")
    })
    @PostMapping(value = "/conversation/{conversationId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> uploadChatImage(
            @Parameter(description = "Conversation ID", example = "64f1a2b3c4d5e6f7a8b9c0d2")
            @PathVariable String conversationId,
            @Parameter(description = "Image file to send", required = true)
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Store to DB and create message record
        Message saved = messageService.saveImageMessage(conversationId, file, userDetails.getUsername());
        ChatMessageResponse response = messageService.toResponse(saved);

        // Get username's list of the conversation and send to each of them
        List<String> participants = conversationService.getParticipantUsernames(saved.getConversation_id());
        for (String participant : participants) {
            messagingTemplate.convertAndSendToUser(
                    participant,
                    "/queue/messages",
                    response
            );
        }

        // @TODO: Handle notification for offline users (Background/Killed app)
        // Call Notification Service to send push notification to offline users ("User X sent a photo")

        return ResponseEntity.status(201).body(response);
    }

    @Operation(
            summary = "Mark conversation messages as read",
            description = "Adds read receipts for unread messages in the conversation for the current user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Read status updated successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or expired access token"),
            @ApiResponse(responseCode = "403", description = "User is not a participant of this conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping("/conversation/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markMessagesAsRead(
            @Parameter(description = "Conversation ID", example = "64f1a2b3c4d5e6f7a8b9c0d2")
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        int updated = messageService.markMessagesAsRead(conversationId, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "conversation_id", conversationId,
                "updated_count", updated
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "Bad request" : ex.getMessage();

        if (msg.startsWith("Forbidden")) {
            return ResponseEntity.status(403).body(Map.of("error", msg));
        }
        if (msg.contains("not found") || msg.contains("required")) {
            return ResponseEntity.status(404).body(Map.of("error", msg));
        }

        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
