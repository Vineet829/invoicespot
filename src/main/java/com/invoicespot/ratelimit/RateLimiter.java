package com.invoicespot.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();
    private final TimeMeter timeMeter;

    public RateLimiter(Clock clock) {
        this.timeMeter = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                Instant now = clock.instant();
                return now.getEpochSecond() * 1_000_000_000L + now.getNano();
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    public void consume(RateLimit limit, String key) {
        ConsumptionProbe probe = bucket(limit, key).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void requireAvailable(RateLimit limit, String key) {
        EstimationProbe probe = bucket(limit, key).estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw limited(limit, probe.getNanosToWaitForRefill());
        }
    }

    public void recordFailure(RateLimit limit, String key) {
        bucket(limit, key).tryConsume(1);
    }

    public void reset(RateLimit limit, String key) {
        buckets.invalidate(cacheKey(limit, key));
    }

    private Bucket bucket(RateLimit limit, String key) {
        return buckets.get(cacheKey(limit, key), ignored -> Bucket.builder()
                .addLimit(limit.bandwidth())
                .withCustomTimePrecision(timeMeter)
                .build());
    }

    private static String cacheKey(RateLimit limit, String key) {
        return limit.name() + ":" + key;
    }

    private static RateLimitedException limited(RateLimit limit, long nanosToWait) {
        return new RateLimitedException(limit.reason(), Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L));
    }
}
