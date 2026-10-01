package com.invoicespot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.metrics.AppMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-09-30T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RateLimiter limiter = new RateLimiter(new LocalBucketStore(clock), new AppMetrics(registry));

    @Test
    @DisplayName("requests beyond the limit are refused with how long to wait, then allowed as tokens refill")
    void limitsAndRefills() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.consume(RateLimit.SIGN_UP, "10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.1"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessage("Too many sign-ups from this network. Please try again in 12 minutes.")
                .extracting(e -> ((RateLimitedException) e).getRetryAfterSeconds())
                .isEqualTo(720L);
        assertThat(registry.get("invoicespot.ratelimit.rejections").tag("limit", "sign_up").counter().count())
                .isEqualTo(1);
        assertThatCode(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.2")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(12));
        assertThatCode(() -> limiter.consume(RateLimit.SIGN_UP, "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("only recorded failures count toward the sign-in limit, and a reset clears them")
    void failures() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|ada");
            limiter.recordFailure(RateLimit.FAILED_SIGN_IN, "ip|ada");
        }
        assertThatThrownBy(() -> limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|ada"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageStartingWith("Too many sign-in attempts. Please try again in 3 minutes");

        limiter.reset(RateLimit.FAILED_SIGN_IN, "ip|ada");
        assertThatCode(() -> limiter.requireAvailable(RateLimit.FAILED_SIGN_IN, "ip|ada")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("waits under a minute are counted in seconds")
    void seconds() {
        assertThat(new RateLimitedException("Slow down", 1).getMessage()).isEqualTo("Slow down. Please try again in 1 second.");
        assertThat(new RateLimitedException("Slow down", 42).getMessage()).isEqualTo("Slow down. Please try again in 42 seconds.");
        assertThat(new RateLimitedException("Slow down", 61).getMessage()).isEqualTo("Slow down. Please try again in 2 minutes.");
    }
}
