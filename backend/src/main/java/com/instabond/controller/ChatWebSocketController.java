package com.instabond.controller;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.entity.Message;
import com.instabond.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User must be authenticated to send messages");
        }

        Message saved = messageService.saveTextMessage(request, principal.getName());
        ChatMessageResponse response = messageService.toResponse(saved);

        // Broadcast the message to all subscribers of the conversation topic
        messagingTemplate.convertAndSend("/topic/conversations/" + saved.getConversation_id(), response);
    }
}
