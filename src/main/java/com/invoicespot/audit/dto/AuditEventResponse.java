package com.invoicespot.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        @JsonProperty("_id") UUID id,
        String type,
        String email,
        String actorEmail,
        String business,
        String detail,
        String ip,
        String device,
        Instant occurredAt) {}
