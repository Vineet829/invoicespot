package com.invoicespot.ratelimit;

import com.invoicespot.metrics.AppMetrics;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final BucketStore buckets;
    private final AppMetrics metrics;

    RateLimiter(BucketStore buckets, AppMetrics metrics) {
        this.buckets = buckets;
        this.metrics = metrics;
    }

    public void consume(RateLimit limit, String key) {
        ConsumptionProbe probe = buckets.bucket(limit, key).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void requireAvailable(RateLimit limit, String key) {
        EstimationProbe probe = buckets.bucket(limit, key).estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void recordFailure(RateLimit limit, String key) {
        buckets.bucket(limit, key).tryConsume(1);
    }

    public void reset(RateLimit limit, String key) {
        buckets.reset(limit, key);
    }

    // For the daily jobs: removes stored buckets that are full again, and
    // returns how many.
    public int removeExpired() {
        return buckets.removeExpired();
    }

    private RateLimitedException limited(RateLimit limit, long nanosToWait) {
        metrics.rateLimited(limit);
        return new RateLimitedException(limit.reason(), Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L));
    }
}
