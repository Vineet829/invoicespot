package com.invoicespot.notification;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.notification.dto.MarkReadRequest;
import com.invoicespot.notification.dto.NotificationListResponse;
import com.invoicespot.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse latest(@AuthenticationPrincipal User principal) {
        return notificationService.latest(principal.getPkid());
    }

    @PostMapping("/read")
    public MessageResponse markRead(
            @AuthenticationPrincipal User principal, @RequestBody(required = false) MarkReadRequest request) {
        notificationService.markRead(principal.getPkid(), request == null ? null : request.id());
        return new MessageResponse(true, "Marked as read.");
    }
}
