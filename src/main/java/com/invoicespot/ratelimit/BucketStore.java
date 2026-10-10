package com.invoicespot.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Instant;

// Where the buckets behind each rate limit live: in this instance's memory, or
// in the database so every instance shares them.
interface BucketStore {

    Bucket bucket(RateLimit limit, String key);

    void reset(RateLimit limit, String key);

    int removeExpired();

    // Buckets refill by the app's clock, so tests can move time on.
    static TimeMeter timeMeter(Clock clock) {
        return new TimeMeter() {
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
}
