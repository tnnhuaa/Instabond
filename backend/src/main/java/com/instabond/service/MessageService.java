package com.instabond.service;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.entity.Conversation;
import com.instabond.entity.Message;
import com.instabond.entity.User;
import com.instabond.repository.ConversationRepository;
import com.instabond.repository.MessageRepository;
import com.instabond.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    public Message saveTextMessage(ChatMessageRequest request, String senderEmail) {
        if (request == null) {
            throw new RuntimeException("Message payload is not valid");
        }

        String conversationId = trimToNull(request.getConversation_id());
        String content = trimToNull(request.getContent());
        String type = normalizeType(request.getType());

        if (conversationId == null) {
            throw new RuntimeException("conversation_id is required");
        }
        if (content == null) {
            throw new RuntimeException("Content is required");
        }
        if (!"text".equals(type)) {
            throw new RuntimeException("saveTextMessage only support type = 'text'");
        }

        User sender = resolveUserByEmail(senderEmail);
        Conversation conversation = resolveConversationAndValidateParticipant(conversationId, sender.getId());

        Message message = Message.builder()
                .conversation_id(conversationId)
                .sender_id(sender.getId())
                .type("text")
                .content(content)
                .is_view_once(false)
                .is_viewed(false)
                .reactions(new ArrayList<>())
                .read_by(new ArrayList<>())
                .created_at(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        updateConversationLastMessage(conversation, saved);
        return saved;
    }

    public Message saveImageMessage(String conversationId, MultipartFile file, String senderEmail) {
        if (trimToNull(conversationId) == null) {
            throw new RuntimeException("conversation_id is required");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File image is not valid");
        }

        User sender = resolveUserByEmail(senderEmail);
        Conversation conversation = resolveConversationAndValidateParticipant(conversationId, sender.getId());

        String imageUrl = fileService.uploadImage(file);
        Message message = Message.builder()
                .conversation_id(conversationId)
                .sender_id(sender.getId())
                .type("image")
                .content(imageUrl)
                .is_view_once(false)
                .is_viewed(false)
                .reactions(new ArrayList<>())
                .read_by(new ArrayList<>())
                .created_at(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        updateConversationLastMessage(conversation, saved);
        return saved;
    }

    public List<Message> getConversationHistory(String conversationId, String requesterEmail, int page, int size) {
        if (trimToNull(conversationId) == null) {
            throw new RuntimeException("conversation_id is required");
        }

        // Limit size to prevent users from requesting too many messages at once
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        User requester = resolveUserByEmail(requesterEmail);
        resolveConversationAndValidateParticipant(conversationId, requester.getId());

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "created_at"));
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
    }

    public int markMessagesAsRead(String conversationId, String readerEmail) {
        if (trimToNull(conversationId) == null) {
            throw new RuntimeException("conversation_id is required");
        }

        User reader = resolveUserByEmail(readerEmail);
        resolveConversationAndValidateParticipant(conversationId, reader.getId());

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Instant readAt = Instant.now();
        int updatedCount = 0;

        for (Message message : messages) {
            if (reader.getId().equals(message.getSender_id())) {
                continue;
            }

            List<Message.ReadReceipt> readBy = message.getRead_by();
            if (readBy == null) {
                readBy = new ArrayList<>();
                message.setRead_by(readBy);
            }

            boolean alreadyRead = readBy.stream()
                    .anyMatch(receipt -> reader.getId().equals(receipt.getUser_id()));

            if (!alreadyRead) {
                readBy.add(Message.ReadReceipt.builder()
                        .user_id(reader.getId())
                        .read_at(readAt)
                        .build());
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            messageRepository.saveAll(messages);
        }

        return updatedCount;
    }

    public ChatMessageResponse toResponse(Message message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversation_id(message.getConversation_id())
                .sender_id(message.getSender_id())
                .type(message.getType())
                .content(message.getContent())
                .created_at(message.getCreated_at())
                .build();
    }

    private User resolveUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found: " + email));
    }

    private Conversation resolveConversationAndValidateParticipant(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conservation not found: " + conversationId));

        // Ensure the user is a participant of the conversation before allowing them to send messages or view history
        if (conversation.getParticipants() == null || !conversation.getParticipants().contains(userId)) {
            throw new RuntimeException("Forbidden: User is not a participant of this conversation");
        }

        return conversation;
    }

    private void updateConversationLastMessage(Conversation conversation, Message savedMessage) {
        conversation.setLast_message(Conversation.LastMessage.builder()
                .content(savedMessage.getContent())
                .sender_id(savedMessage.getSender_id())
                .sent_at(savedMessage.getCreated_at())
                .is_read(false)
                .build());
        conversation.setUpdated_at(savedMessage.getCreated_at());
        conversationRepository.save(conversation);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeType(String type) {
        String normalized = trimToNull(type);
        if (normalized == null) {
            return "text";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
