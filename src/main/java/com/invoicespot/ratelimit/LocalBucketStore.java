package com.invoicespot.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;

// Buckets in this instance's memory, for a single instance.
class LocalBucketStore implements BucketStore {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();
    private final TimeMeter timeMeter;

    LocalBucketStore(Clock clock) {
        this.timeMeter = BucketStore.timeMeter(clock);
    }

    @Override
    public Bucket bucket(RateLimit limit, String key) {
        return buckets.get(cacheKey(limit, key), ignored -> Bucket.builder()
                .addLimit(limit.bandwidth())
                .withCustomTimePrecision(timeMeter)
                .build());
    }

    @Override
    public void reset(RateLimit limit, String key) {
        buckets.invalidate(cacheKey(limit, key));
    }

    // The cache drops idle buckets by itself.
    @Override
    public int removeExpired() {
        return 0;
    }

    private static String cacheKey(RateLimit limit, String key) {
        return limit.name() + ":" + key;
    }
}
