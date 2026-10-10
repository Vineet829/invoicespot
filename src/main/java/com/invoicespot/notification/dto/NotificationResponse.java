package com.invoicespot.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.notification.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        @JsonProperty("_id") UUID id, String type, String message, UUID documentId, Instant createdAt, boolean read) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.getDocument() == null ? null : notification.getDocument().getId(),
                notification.getCreatedAt(),
                notification.getReadAt() != null);
    }
}
