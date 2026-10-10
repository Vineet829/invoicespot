package com.invoicespot.ratelimit;

import com.invoicespot.common.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException {

    private static final long BUSY_RETRY_SECONDS = 5;

    private final long retryAfterSeconds;

    public RateLimitedException(String reason, long retryAfterSeconds) {
        this(HttpStatus.TOO_MANY_REQUESTS, reason, retryAfterSeconds);
    }

    private RateLimitedException(HttpStatus status, String reason, long retryAfterSeconds) {
        super(status, reason + ". Please try again in " + describe(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    // The limits can't be checked right now, as when a flood of requests keeps
    // the limiter's database busy: the server is overloaded, not this client.
    static RateLimitedException busy() {
        return new RateLimitedException(
                HttpStatus.SERVICE_UNAVAILABLE, "Too many requests right now", BUSY_RETRY_SECONDS);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String describe(long seconds) {
        if (seconds < 60) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
