package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

// The document's share link, if it has one; its address only when it can be
// shown again.
public record ShareStatusResponse(
        boolean success,
        boolean active,
        Instant createdAt,
        Instant lastViewedAt,
        int viewCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String url) {}
