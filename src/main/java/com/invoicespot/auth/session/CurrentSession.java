package com.invoicespot.auth.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// The session a request's access token belongs to, and the business that
// session is working in, kept with the request's authentication.
public final class CurrentSession {

    public record Details(UUID sessionId, Long businessPkid) {}

    private CurrentSession() {}

    public static Optional<UUID> id() {
        return details().map(Details::sessionId);
    }

    public static Optional<Long> businessPkid() {
        return details().map(Details::businessPkid);
    }

    private static Optional<Details> details() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getDetails() instanceof Details details
                ? Optional.of(details)
                : Optional.empty();
    }
}
