package com.invoicespot.notification;

import com.invoicespot.auth.dto.MessageResponse;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import com.invoicespot.notification.dto.MarkReadRequest;
import com.invoicespot.notification.dto.NotificationListResponse;
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
    public NotificationListResponse latest(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return notificationService.latest(access);
    }

    @PostMapping("/read")
    public MessageResponse markRead(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestBody(required = false) MarkReadRequest request) {
        notificationService.markRead(access, request == null ? null : request.id());
        return new MessageResponse(true, "Marked as read.");
    }
}
