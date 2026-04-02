package com.instabond.service;

import com.instabond.dto.ChatMessageRequest;
import com.instabond.dto.ChatMessageResponse;
import com.instabond.entity.Conversation;
import com.instabond.entity.Message;
import com.instabond.entity.User;
import com.instabond.exception.ForbiddenOperationException;
import com.instabond.exception.ResourceNotFoundException;
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
            throw new IllegalArgumentException("Message payload is not valid");
        }

        String conversationId = trimToNull(request.getConversationId());
        String content = trimToNull(request.getContent());
        String type = normalizeType(request.getType());

        if (conversationId == null) {
            throw new IllegalArgumentException("conversation_id is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }


        User sender = resolveUserByEmail(senderEmail);
        Conversation conversation = resolveConversationAndValidateParticipant(conversationId, sender.getId());

        Message message = Message.builder()
                .conversation_id(conversationId)
                .sender_id(sender.getId())
                .type(type)
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
            throw new IllegalArgumentException("conversation_id is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File image is not valid");
        }

        User sender = resolveUserByEmail(senderEmail);
        Conversation conversation = resolveConversationAndValidateParticipant(conversationId, sender.getId());

        String imageUrl = String.valueOf(fileService.uploadImage(file));
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
            throw new IllegalArgumentException("conversation_id is required");
        }

        // Limit size to prevent users from requesting too many messages at once
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        User requester = resolveUserByEmail(requesterEmail);
        resolveConversationAndValidateParticipant(conversationId, requester.getId());

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "created_at"));
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
    }

    public int markMessagesAsRead(String conversationId, String readerEmail) {
        if (trimToNull(conversationId) == null) {
            throw new IllegalArgumentException("conversation_id is required");
        }

        User reader = resolveUserByEmail(readerEmail);
        resolveConversationAndValidateParticipant(conversationId, reader.getId());

        // Filter messages
        List<Message> unreadMessages = messageRepository.findUnreadMessages(conversationId, reader.getId());

        if (unreadMessages.isEmpty()) {
            return 0;
        }

        Instant readAt = Instant.now();

        for (Message message : unreadMessages) {
            List<Message.ReadReceipt> readBy = message.getRead_by();
            if (readBy == null) {
                readBy = new ArrayList<>();
                message.setRead_by(readBy);
            }

            readBy.add(Message.ReadReceipt.builder()
                    .user_id(reader.getId())
                    .read_at(readAt)
                    .build());

            message.set_viewed(true);
        }

        // Save all updated messages
        messageRepository.saveAll(unreadMessages);

        return unreadMessages.size();
    }

    public ChatMessageResponse toResponse(Message message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation_id())
                .senderId(message.getSender_id())
                .type(message.getType())
                .content(message.getContent())
                .previewText(buildMessagePreview(message))
                .createdAt(message.getCreated_at())
                .build();
    }

    public String buildMessagePreview(Message message) {
        if (message == null) {
            return "";
        }

        String type = normalizeType(message.getType());
        return switch (type) {
            case "image" -> "sent a photo";
            case "post_share" -> "shared a post";
            case "story_reply" -> "replied to your story";
            default -> {
                String content = trimToNull(message.getContent());
                yield content == null ? "" : content;
            }
        };
    }

    private User resolveUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + email));
    }

    private Conversation resolveConversationAndValidateParticipant(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        // Ensure the user is a participant of the conversation before allowing them to
        // send messages or view history
        if (conversation.getParticipants() == null || !conversation.getParticipants().contains(userId)) {
            throw new ForbiddenOperationException("User is not a participant of this conversation");
        }

        return conversation;
    }

    private void updateConversationLastMessage(Conversation conversation, Message savedMessage) {
        conversation.setLast_message(Conversation.LastMessage.builder()
                .content(buildMessagePreview(savedMessage))
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
