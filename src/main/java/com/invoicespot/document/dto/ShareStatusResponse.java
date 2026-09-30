package com.invoicespot.document.dto;

import java.time.Instant;

public record ShareStatusResponse(
        boolean success, boolean active, Instant createdAt, Instant lastViewedAt, int viewCount) {}
