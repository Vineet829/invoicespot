package com.invoicespot.notification;

import com.invoicespot.common.ApiException;
import com.invoicespot.document.Document;
import com.invoicespot.notification.dto.NotificationListResponse;
import com.invoicespot.notification.dto.NotificationResponse;
import com.invoicespot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int LATEST = 30;
    private static final int MESSAGE_LENGTH = 300;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public void notify(User user, Document document, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setDocument(document);
        notification.setType(type);
        notification.setMessage(message.length() > MESSAGE_LENGTH ? message.substring(0, MESSAGE_LENGTH) : message);
        notification.setCreatedAt(Instant.now(clock));
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse latest(Long userPkid) {
        return new NotificationListResponse(
                true,
                notificationRepository.countByUserPkidAndReadAtIsNull(userPkid),
                notificationRepository.findByUserPkidOrderByCreatedAtDescPkidDesc(userPkid, PageRequest.of(0, LATEST))
                        .stream()
                        .map(NotificationResponse::from)
                        .toList());
    }

    // One notification, or all of them when no id is given.
    @Transactional
    public void markRead(Long userPkid, String id) {
        Instant now = Instant.now(clock);
        if (id == null || id.isBlank()) {
            notificationRepository.markAllRead(userPkid, now);
            return;
        }
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        Notification notification = notificationRepository.findByIdAndUserPkid(externalId, userPkid)
                .orElseThrow(NotificationService::notFound);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
    }
}
