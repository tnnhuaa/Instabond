package com.instabond.controller;

import com.instabond.entity.Conversation;
import com.instabond.service.ConversationService;
import com.instabond.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "REST APIs for managing chat rooms and inboxes")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserService userService;

    @Operation(
            summary = "Get or Create Direct Conversation",
            description = "Find a 1-1 chat room between the current user and partnerId. If no prior chat exists, the system will automatically create a new chat room and return its information."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Return conversation details (existing or newly created)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Conversation.class))),
            @ApiResponse(responseCode = "400", description = "Logic error such as trying to chat with oneself",
                    content = @Content)
    })
    @PostMapping("/direct")
    public ResponseEntity<Conversation> getOrCreateDirectConversation(
            @Parameter(description = "ID of user", required = true, example = "65e2a1b2c3d4e5f6g7h8i9j0")
            @RequestParam String partnerId,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Get email from token
        String email = userDetails.getUsername();

        // Query database for current user ID
        String currentUserId = userService.getUserIdByEmail(email);

        Conversation conversation = conversationService.getOrCreateDirectConversation(
                currentUserId,
                partnerId
        );

        return ResponseEntity.ok(conversation);
    }
}
