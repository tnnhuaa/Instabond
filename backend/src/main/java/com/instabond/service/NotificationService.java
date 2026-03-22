package com.instabond.service;

import com.instabond.dto.WsEvent;
import com.instabond.entity.Notification;
import com.instabond.entity.User;
import com.instabond.repository.NotificationRepository;
import com.instabond.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String EVENTS_DESTINATION = "/queue/events";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Notification saveAndSendChatNotification(String senderId, String recipientId, String conversationId, String content) {
        Notification savedNotification = notificationRepository.save(
                Notification.builder()
                        .sender_id(senderId)
                        .recipient_id(recipientId)
                        .type("NEW_MESSAGE")
                        .content(content)
                        .is_read(false)
                        .metadata(Notification.Metadata.builder()
                                .conversation_id(conversationId)
                                .build())
                        .created_at(Instant.now())
                        .build()
        );

        WsEvent<Notification> notificationEvent = WsEvent.of(WsEvent.TYPE_NOTIFICATION, savedNotification);

        messagingTemplate.convertAndSendToUser(
                recipientId,
                EVENTS_DESTINATION,
                notificationEvent
        );

        // Current WebSocket principal uses email, so send to email channel as a compatibility path.
        userRepository.findById(recipientId)
                .map(User::getEmail)
                .filter(email -> !recipientId.equals(email))
                .ifPresent(email -> messagingTemplate.convertAndSendToUser(
                        email,
                        EVENTS_DESTINATION,
                        notificationEvent
                ));

        return savedNotification;
    }

    public void sendPushNotification(String recipientId, String title, String body) {
        log.info("[FCM-MOCK] recipientId={}, title='{}', body='{}'", recipientId, title, body);
    }

    public Notification sendLikeNotification(String senderId, String recipientId, String postId) {
        User sender = userRepository.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getUsername() : "Someone";
        
        Notification savedNotification = notificationRepository.save(
                Notification.builder()
                        .sender_id(senderId)
                        .recipient_id(recipientId)
                        .type("LIKE")
                        .content(senderName + " liked your post")
                        .is_read(false)
                        .metadata(Notification.Metadata.builder()
                                .post_id(postId)
                                .build())
                        .created_at(Instant.now())
                        .build()
        );

        sendNotificationViaWebSocket(savedNotification, recipientId);
        return savedNotification;
    }

    public Notification sendCommentNotification(String senderId, String recipientId, String postId, String commentContent) {
        User sender = userRepository.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getUsername() : "Someone";
        String truncatedContent = commentContent != null && commentContent.length() > 50 
            ? commentContent.substring(0, 50) + "..." 
            : commentContent;
        
        Notification savedNotification = notificationRepository.save(
                Notification.builder()
                        .sender_id(senderId)
                        .recipient_id(recipientId)
                        .type("COMMENT")
                        .content(senderName + " commented: " + truncatedContent)
                        .is_read(false)
                        .metadata(Notification.Metadata.builder()
                                .post_id(postId)
                                .build())
                        .created_at(Instant.now())
                        .build()
        );

        sendNotificationViaWebSocket(savedNotification, recipientId);
        return savedNotification;
    }

    public Notification sendFollowNotification(String senderId, String recipientId, String status) {
        User sender = userRepository.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getUsername() : "Someone";
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        String content = "";
        if ("pending".equals(normalizedStatus)) {
            content = senderName + " sent you a follow request";
        } else if ("accepted".equals(normalizedStatus)) {
            content = senderName + " accepted your follow request";
        } else if ("followed".equals(normalizedStatus)) {
            content = senderName + " started following you";
        }
        
        Notification savedNotification = notificationRepository.save(
                Notification.builder()
                        .sender_id(senderId)
                        .recipient_id(recipientId)
                        .type("FOLLOW")
                        .content(content)
                        .is_read(false)
                        .metadata(Notification.Metadata.builder()
                                .build())
                        .created_at(Instant.now())
                        .build()
        );

        sendNotificationViaWebSocket(savedNotification, recipientId);
        return savedNotification;
    }

    private void sendNotificationViaWebSocket(Notification notification, String recipientId) {
        WsEvent<Notification> notificationEvent = WsEvent.of(WsEvent.TYPE_NOTIFICATION, notification);

        messagingTemplate.convertAndSendToUser(
                recipientId,
                EVENTS_DESTINATION,
                notificationEvent
        );

        // Current WebSocket principal uses email, so send to email channel as a compatibility path.
        userRepository.findById(recipientId)
                .map(User::getEmail)
                .filter(email -> !recipientId.equals(email))
                .ifPresent(email -> messagingTemplate.convertAndSendToUser(
                        email,
                        EVENTS_DESTINATION,
                        notificationEvent
                ));
    }

    public NotificationPage getUserNotifications(String recipientId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<Notification> notifications = notificationRepository.findByRecipientId(recipientId, pageable);
        long totalElements = notificationRepository.countByRecipientId(recipientId);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        return NotificationPage.builder()
                .data(notifications)
                .page(safePage)
                .size(safeSize)
                .total_elements(totalElements)
                .total_pages(totalPages)
                .has_next(safePage + 1 < totalPages)
                .build();
    }

    public Notification markAsRead(String notificationId, String recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        if (!recipientId.equals(notification.getRecipient_id())) {
            throw new RuntimeException("Forbidden: this notification does not belong to current user");
        }

        if (notification.is_read()) {
            return notification;
        }

        notification.set_read(true);
        return notificationRepository.save(notification);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    @Getter
    @Builder
    public static class NotificationPage {
        private List<Notification> data;
        private int page;
        private int size;
        private long total_elements;
        private int total_pages;
        private boolean has_next;
    }
}
