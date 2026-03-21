package com.instabond.controller;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.dto.WsEvent;
import com.instabond.entity.Message;
import com.instabond.service.ConversationService;
import com.instabond.service.MessageService;
import com.instabond.service.NotificationService;
import com.instabond.service.PresenceService;
import com.instabond.service.UserService;
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

    private static final String EVENTS_DESTINATION = "/queue/events";

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationService conversationService;
    private final PresenceService presenceService;
    private final NotificationService notificationService;
    private final UserService userService;

    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessageRequest request, Principal principal) {
        System.out.println("Received message: " + request + " from user: " + (principal != null ? principal.getName() : "null"));

        if (principal == null) {
            throw new RuntimeException("User must be authenticated to send messages");
        }

        // Store to DB
        Message saved = messageService.saveTextMessage(request, principal.getName());
        ChatMessageResponse response = messageService.toResponse(saved);
        WsEvent<ChatMessageResponse> chatEvent = WsEvent.of(WsEvent.TYPE_CHAT, response);

        // Get username's list of the conversation and send to each of them
        List<String> participants = conversationService.getParticipantEmail(saved.getConversation_id());

        for (String participant : participants) {
            messagingTemplate.convertAndSendToUser(
                    participant,
                    EVENTS_DESTINATION,
                    chatEvent
            );

            if (principal.getName().equals(participant)) {
                continue;
            }

            String recipientId = userService.getUserIdByEmail(participant);

            notificationService.saveAndSendChatNotification(
                    saved.getSender_id(),
                    recipientId,
                    saved.getConversation_id(),
                    saved.getContent()
            );

            if (!presenceService.isOnline(participant)) {
                notificationService.sendPushNotification(
                        recipientId,
                        "New message",
                        saved.getContent()
                );
            }
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/events")
    public WsEvent<Map<String, String>> handleException(RuntimeException ex) {
        System.out.println("Error: " + ex.getMessage());

        return WsEvent.of(
                WsEvent.TYPE_ERROR,
                Map.of(
                        "status", "error",
                        "message", "Failed to send message: " + ex.getMessage()
                )
        );
    }
}
