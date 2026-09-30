package com.invoicespot.document.dto;

import com.invoicespot.document.DocumentEvent;
import com.invoicespot.user.User;
import java.time.Instant;

// One event in a document's timeline. The actor is the member who did it,
// by name, or null when no member did; byYou marks the viewer's own.
public record ActivityEntry(String type, String detail, Instant at, String actor, boolean byYou) {

    public static ActivityEntry from(DocumentEvent event, Long viewerPkid) {
        User actor = event.getActor();
        return new ActivityEntry(
                event.getType().name(),
                event.getDetail(),
                event.getOccurredAt(),
                actor == null ? null : actor.displayName(),
                actor != null && actor.getPkid().equals(viewerPkid));
    }
}
