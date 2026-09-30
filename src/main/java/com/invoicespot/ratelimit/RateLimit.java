package com.invoicespot.ratelimit;

import io.github.bucket4j.Bandwidth;
import java.time.Duration;

public enum RateLimit {
    FAILED_SIGN_IN(5, Duration.ofMinutes(15), "Too many sign-in attempts"),
    SIGN_UP(5, Duration.ofHours(1), "Too many sign-ups from this network"),
    PASSWORD_RESET(5, Duration.ofHours(1), "Too many password reset requests"),
    VERIFICATION_EMAIL(5, Duration.ofHours(1), "Too many verification emails requested"),
    OUTGOING_EMAIL(30, Duration.ofHours(1), "Too many emails sent"),
    SHARED_DOCUMENT(60, Duration.ofMinutes(1), "Too many requests for shared documents");

    private final int capacity;
    private final Duration period;
    private final String reason;

    RateLimit(int capacity, Duration period, String reason) {
        this.capacity = capacity;
        this.period = period;
        this.reason = reason;
    }

    Bandwidth bandwidth() {
        return Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
    }

    String reason() {
        return reason;
    }
}
