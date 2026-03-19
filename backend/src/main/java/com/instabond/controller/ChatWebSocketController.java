package com.instabond.controller;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.entity.Message;
import com.instabond.service.MessageService;
import com.instabond.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationService conversationService;

    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User must be authenticated to send messages");
        }

        // Store to DB
        Message saved = messageService.saveTextMessage(request, principal.getName());
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
        // Call Notification Service to send push notification to offline users
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> handleException(RuntimeException ex) {
        return Map.of(
                "status", "error",
                "message", "Failed to send message: " + ex.getMessage()
        );
    }
}
