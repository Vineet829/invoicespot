package com.invoicespot.ratelimit;

import com.invoicespot.common.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(String reason, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason + ". Please try again in " + describe(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
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
