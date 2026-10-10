package com.invoicespot.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ActivityEventResponse(
        @JsonProperty("_id") UUID id,
        String type,
        String detail,
        String ip,
        String device,
        Instant occurredAt,
        boolean thisDevice) {}
