package com.invoicespot.ratelimit;

import io.github.bucket4j.Bandwidth;
import java.time.Duration;

public enum RateLimit {
    FAILED_SIGN_IN(5, Duration.ofMinutes(15), "Too many sign-in attempts"),
    // Across every email, so trying one password on many accounts stops too.
    FAILED_SIGN_IN_ADDRESS(30, Duration.ofMinutes(15), "Too many sign-in attempts from this network"),
    WRONG_PASSWORD(5, Duration.ofMinutes(15), "Too many wrong passwords"),
    WRONG_TWO_FACTOR_CODE(5, Duration.ofMinutes(15), "Too many wrong codes"),
    SIGN_UP(5, Duration.ofHours(1), "Too many sign-ups from this network"),
    PASSWORD_RESET(5, Duration.ofHours(1), "Too many password reset requests"),
    VERIFICATION_EMAIL(5, Duration.ofHours(1), "Too many verification emails requested"),
    OUTGOING_EMAIL(30, Duration.ofHours(1), "Too many emails sent"),
    SHARED_DOCUMENT(60, Duration.ofMinutes(1), "Too many requests for shared documents"),
    PAYMENT_CLAIM(5, Duration.ofHours(1), "Too many payments reported from this network"),
    CLIENT_PORTAL(60, Duration.ofMinutes(1), "Too many requests for client portals"),
    INVITATION(20, Duration.ofHours(1), "Too many invitations sent"),
    INVITATION_LOOKUP(30, Duration.ofMinutes(1), "Too many requests for invitations"),
    // A business's whole archive is built in memory each time.
    WHOLE_EXPORT(10, Duration.ofHours(1), "Too many exports of everything");

    private final int capacity;
    private final Duration period;
    private final String reason;
    // Buckets kept in the database hold on to the numbers they were made
    // with. Raise a limit's version when changing its numbers, and its stored
    // buckets start over with the new ones.
    private final int version;

    RateLimit(int capacity, Duration period, String reason) {
        this(capacity, period, reason, 1);
    }

    RateLimit(int capacity, Duration period, String reason, int version) {
        this.capacity = capacity;
        this.period = period;
        this.reason = reason;
        this.version = version;
    }

    Bandwidth bandwidth() {
        return Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
    }

    String reason() {
        return reason;
    }

    int version() {
        return version;
    }
}
