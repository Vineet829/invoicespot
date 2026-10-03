package com.invoicespot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.SecretTokens;
import com.invoicespot.config.AppProperties;
import com.invoicespot.metrics.AppMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// The app's own limiter keeps its buckets in the test database. Each limiter
// made by server() stands in for another instance of the app: its own store
// and connection pool, on the same database.
class SharedRateLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private JdbcConnectionDetails connection;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AppProperties properties;

    // The app's own store, which names the rows its limiter writes.
    @Autowired
    private PostgresBucketStore appStore;

    private final List<PostgresBucketStore> stores = new ArrayList<>();

    private RateLimiter server(Clock clock) {
        return server(clock, properties.jwt().accessSecret());
    }

    private RateLimiter server(Clock clock, String secret) {
        PostgresBucketStore store = new PostgresBucketStore(connection, clock, secret);
        stores.add(store);
        return new RateLimiter(store, new AppMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void closePools() {
        stores.forEach(PostgresBucketStore::close);
    }

    private static String address() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250)
                + "." + (int) (Math.random() * 250);
    }

    private String id(RateLimit limit, String key) {
        return appStore.id(limit, key);
    }

    private List<String> storedIds() {
        return jdbcTemplate.queryForList("select id from rate_limit_buckets", String.class);
    }

    @Test
    @DisplayName("two servers draw on one budget, refuse alike once it is spent, and refill alike")
    void oneBudget() {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        RateLimiter first = server(clock);
        RateLimiter second = server(clock);
        String address = address();
        for (int attempt = 0; attempt < 5; attempt++) {
            (attempt % 2 == 0 ? first : second).consume(RateLimit.SIGN_UP, address);
        }

        assertThatThrownBy(() -> first.consume(RateLimit.SIGN_UP, address))
                .isInstanceOf(RateLimitedException.class)
                .hasMessage("Too many sign-ups from this network. Please try again in 12 minutes.");
        assertThatThrownBy(() -> second.consume(RateLimit.SIGN_UP, address))
                .isInstanceOf(RateLimitedException.class)
                .hasMessage("Too many sign-ups from this network. Please try again in 12 minutes.");
        assertThatCode(() -> second.consume(RateLimit.SIGN_UP, address())).doesNotThrowAnyException();

        RateLimiter later = server(Clock.offset(clock, Duration.ofMinutes(12)));
        assertThatCode(() -> later.consume(RateLimit.SIGN_UP, address)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("failures recorded on one server lock sign-in on the other, and a reset on either clears them")
    void failuresAndResets() {
        RateLimiter first = server(Clock.systemUTC());
        RateLimiter second = server(Clock.systemUTC());
        String attempt = address() + "|ada-" + UUID.randomUUID() + "@example.test";
        for (int failure = 0; failure < 5; failure++) {
            first.requireAvailable(RateLimit.FAILED_SIGN_IN, attempt);
            second.recordFailure(RateLimit.FAILED_SIGN_IN, attempt);
        }

        assertThatThrownBy(() -> first.requireAvailable(RateLimit.FAILED_SIGN_IN, attempt))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageStartingWith("Too many sign-in attempts. Please try again in 3 minutes");

        second.reset(RateLimit.FAILED_SIGN_IN, attempt);
        assertThatCode(() -> first.requireAvailable(RateLimit.FAILED_SIGN_IN, attempt)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a failure recorded in a transaction that rolls back still counts")
    void outlivesRollback() {
        String attempts = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            for (int failure = 0; failure < 5; failure++) {
                rateLimiter.recordFailure(RateLimit.WRONG_PASSWORD, attempts);
            }
            transaction.setRollbackOnly();
        });

        assertThatThrownBy(() -> rateLimiter.requireAvailable(RateLimit.WRONG_PASSWORD, attempts))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageStartingWith("Too many wrong passwords");
    }

    @Test
    @DisplayName("the table keeps each limit with a keyed hash of its key, never the email or address,"
            + " nor a plain hash anyone could match by trying every address")
    void storesHashes() {
        String address = address();
        String email = "hashed-" + UUID.randomUUID() + "@example.test";
        rateLimiter.consume(RateLimit.SIGN_UP, address);
        rateLimiter.recordFailure(RateLimit.FAILED_SIGN_IN, address + "|" + email);

        assertThat(storedIds())
                .contains(id(RateLimit.SIGN_UP, address), id(RateLimit.FAILED_SIGN_IN, address + "|" + email))
                .doesNotContain(
                        RateLimit.SIGN_UP.name() + ":" + SecretTokens.hash(address),
                        RateLimit.FAILED_SIGN_IN.name() + ":" + SecretTokens.hash(address + "|" + email));
        List<String> rows = jdbcTemplate.queryForList(
                "select id || ' ' || encode(coalesce(state, ''), 'escape') || ' ' || coalesce(expires_at, 0)"
                        + " from rate_limit_buckets",
                String.class);
        assertThat(rows).isNotEmpty().noneMatch(row -> row.contains(address) || row.contains(email));
    }

    @Test
    @DisplayName("a server with another secret counts in buckets of its own")
    void keyedBySecret() {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        RateLimiter first = server(clock);
        RateLimiter other = server(clock, "another-access-secret-0123456789abcdef");
        String address = address();
        for (int attempt = 0; attempt < 5; attempt++) {
            first.consume(RateLimit.SIGN_UP, address);
        }

        assertThatThrownBy(() -> first.consume(RateLimit.SIGN_UP, address)).isInstanceOf(RateLimitedException.class);
        // The app's own limiter is keyed with the access-token secret too, so
        // it counts in the same bucket.
        assertThatThrownBy(() -> rateLimiter.consume(RateLimit.SIGN_UP, address))
                .isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> other.consume(RateLimit.SIGN_UP, address)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the daily cleanup removes buckets that have long been full again and keeps the others")
    void removesExpired() {
        String old = address();
        String recent = address();
        rateLimiter.consume(RateLimit.SIGN_UP, old);
        rateLimiter.consume(RateLimit.SIGN_UP, recent);
        // As if the first was written two days ago: its bucket has long been full again.
        jdbcTemplate.update(
                "update rate_limit_buckets set expires_at = expires_at - ? where id = ?",
                Duration.ofDays(2).toMillis(),
                id(RateLimit.SIGN_UP, old));

        assertThat(rateLimiter.removeExpired()).isPositive();
        assertThat(storedIds())
                .doesNotContain(id(RateLimit.SIGN_UP, old))
                .contains(id(RateLimit.SIGN_UP, recent));
    }

    @Test
    @DisplayName("the daily cleanup keeps a bucket whose first write was cut short for a day, then removes it")
    void removesCutShort() {
        String id = id(RateLimit.SIGN_UP, address());
        // Bucket4j inserts the id alone, then sets the state and expiry; the
        // request ended in between.
        jdbcTemplate.update("insert into rate_limit_buckets (id, state) values (?, null)", id);

        rateLimiter.removeExpired();
        assertThat(storedIds()).contains(id);

        // As if it was written two days ago.
        jdbcTemplate.update(
                "update rate_limit_buckets set expires_at = expires_at - ? where id = ?",
                Duration.ofDays(2).toMillis(),
                id);

        rateLimiter.removeExpired();
        assertThat(storedIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("with RATE_LIMIT_SHARED=false each server keeps its buckets in memory")
    void inMemory() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfig.class, RateLimiter.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(AppMetrics.class, () -> new AppMetrics(new SimpleMeterRegistry()))
                .withBean(AppProperties.class, () -> properties)
                .withBean(JdbcConnectionDetails.class, () -> connection);
        runner.run(context -> assertThat(context).hasSingleBean(PostgresBucketStore.class));

        String address = address();
        runner.withPropertyValues("app.rate-limit.shared=false").run(context -> {
            assertThat(context).hasSingleBean(LocalBucketStore.class).doesNotHaveBean(PostgresBucketStore.class);
            RateLimiter limiter = context.getBean(RateLimiter.class);
            for (int attempt = 0; attempt < 5; attempt++) {
                limiter.consume(RateLimit.SIGN_UP, address);
            }
            assertThatThrownBy(() -> limiter.consume(RateLimit.SIGN_UP, address))
                    .isInstanceOf(RateLimitedException.class);
        });

        assertThat(storedIds()).doesNotContain(id(RateLimit.SIGN_UP, address));
        assertThatCode(() -> rateLimiter.consume(RateLimit.SIGN_UP, address)).doesNotThrowAnyException();
    }
}
