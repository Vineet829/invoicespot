package com.invoicespot.ratelimit;

import com.invoicespot.common.SecretTokens;
import com.zaxxer.hikari.HikariDataSource;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import io.github.bucket4j.postgresql.PostgreSQLSelectForUpdateBasedProxyManager;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;

// Buckets in the database, so every instance of the app counts against the
// same limits. A row's id is the limit and a hash of what it limits, so no
// email or address is stored.
//
// It keeps a small connection pool of its own: the limiter is called inside
// transactions that already hold one of the app's connections, and with one
// shared pool, busy requests could each wait for a second connection none can
// get. Bucket4j commits on that connection itself, so what it records stays
// even when the caller's transaction rolls back.
class PostgresBucketStore implements BucketStore, AutoCloseable {

    private static final int POOL_SIZE = 4;
    // A row can be removed five minutes after its bucket is full again.
    private static final Duration KEEP_FULL = Duration.ofMinutes(5);
    private static final int CLEANUP_BATCH = 1_000;

    private final HikariDataSource pool = new HikariDataSource();
    private final PostgreSQLSelectForUpdateBasedProxyManager<String> buckets;

    PostgresBucketStore(JdbcConnectionDetails connection, Clock clock) {
        pool.setPoolName("rate-limits");
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setDriverClassName(connection.getDriverClassName());
        pool.setJdbcUrl(connection.getJdbcUrl());
        pool.setUsername(connection.getUsername());
        pool.setPassword(connection.getPassword());
        buckets = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(pool)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("rate_limit_buckets")
                .idColumn("id")
                .stateColumn("state")
                .expiresAtColumn("expires_at")
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_FULL))
                .clientClock(BucketStore.timeMeter(clock))
                .build();
    }

    @Override
    public Bucket bucket(RateLimit limit, String key) {
        return buckets.builder()
                .withImplicitConfigurationReplacement(limit.version(), TokensInheritanceStrategy.RESET)
                .build(id(limit, key), () -> BucketConfiguration.builder().addLimit(limit.bandwidth()).build());
    }

    @Override
    public void reset(RateLimit limit, String key) {
        buckets.removeProxy(id(limit, key));
    }

    // In batches, so no one delete holds many rows at once.
    @Override
    public int removeExpired() {
        int total = 0;
        int removed;
        do {
            removed = buckets.removeExpired(CLEANUP_BATCH);
            total += removed;
        } while (removed == CLEANUP_BATCH);
        return total;
    }

    @Override
    public void close() {
        pool.close();
    }

    private static String id(RateLimit limit, String key) {
        return limit.name() + ":" + SecretTokens.hash(key);
    }
}
