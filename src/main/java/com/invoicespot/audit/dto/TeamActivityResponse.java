package com.invoicespot.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.audit.AuditEvent;
import com.invoicespot.user.User;
import java.time.Instant;
import java.util.UUID;

// A change to a business's team as its owner and admins see it: who did it,
// who it was about, and when. Addresses and devices stay in the admin log.
public record TeamActivityResponse(
        @JsonProperty("_id") UUID id,
        String type,
        String actor,
        String actorEmail,
        String email,
        String detail,
        Instant occurredAt) {

    public static TeamActivityResponse from(AuditEvent event) {
        User actor = event.getActor();
        return new TeamActivityResponse(
                event.getId(),
                event.getType().name(),
                actor == null ? null : actor.displayName(),
                actor == null ? null : actor.getEmail(),
                event.getEmail(),
                event.getDetail(),
                event.getOccurredAt());
    }
}
