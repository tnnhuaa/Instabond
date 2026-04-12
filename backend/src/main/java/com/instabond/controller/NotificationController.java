package com.instabond.controller;

import com.instabond.entity.Notification;
import com.instabond.service.NotificationService;
import com.instabond.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@NullMarked
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<NotificationService.NotificationPage> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        String recipientId = userService.getUserIdByEmail(userDetails.getUsername());
        NotificationService.NotificationPage response = notificationService.getUserNotifications(recipientId, page, size);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String recipientId = userService.getUserIdByEmail(userDetails.getUsername());
        Notification updatedNotification = notificationService.markAsRead(id, recipientId);

        return ResponseEntity.ok(updatedNotification);
    }
}
