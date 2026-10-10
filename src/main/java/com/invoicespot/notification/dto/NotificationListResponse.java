package com.invoicespot.notification.dto;

import java.util.List;

public record NotificationListResponse(boolean success, long unread, List<NotificationResponse> notifications) {}
