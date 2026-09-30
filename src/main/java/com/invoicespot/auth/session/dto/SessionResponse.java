package com.invoicespot.auth.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        @JsonProperty("_id") UUID id,
        String device,
        String ip,
        Instant signedInAt,
        Instant lastActiveAt,
        boolean current) {}
