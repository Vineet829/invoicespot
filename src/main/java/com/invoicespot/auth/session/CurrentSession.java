package com.invoicespot.auth.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// The session a request's access token belongs to, kept with the request's
// authentication.
public final class CurrentSession {

    public record Details(UUID sessionId) {}

    private CurrentSession() {}

    public static Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getDetails() instanceof Details details
                ? Optional.of(details.sessionId())
                : Optional.empty();
    }
}
