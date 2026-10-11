package com.invoicespot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class RateLimitIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng!password";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private PostgresBucketStore store;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String address() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private User userWithPassword() {
        User user = createUser();
        user.setPassword(passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private ResultActions login(String address, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    @Test
    @DisplayName("five failed sign-ins lock that email from that address, even with the right password")
    void failedSignIns() throws Exception {
        User user = userWithPassword();
        String address = address();
        for (int attempt = 0; attempt < 5; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }

        login(address, user.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value(startsWith("Too many sign-in attempts")));
        login(address(), user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("thirty failed sign-ins from one address, whatever the emails, stop that address for a while")
    void sprayedPasswords() throws Exception {
        User user = userWithPassword();
        String address = address();
        for (int attempt = 0; attempt < 30; attempt++) {
            login(address, "nobody" + attempt + "@example.test", "Spr4y!password")
                    .andExpect(status().isUnauthorized());
        }

        login(address, user.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith("Too many sign-in attempts from this network")));
        login(address(), user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong passwords sent all at once for one email are checked five times at most")
    void attemptsAtOnce() throws Exception {
        User user = userWithPassword();
        String address = address();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int attempt = 0; attempt < 8; attempt++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    return login(address, user.getEmail(), "Wrong!password1").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> attempt : attempts) {
                statuses.add(attempt.get(60, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(status -> status == 401).hasSize(5);
            assertThat(statuses).filteredOn(status -> status == 429).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("an address gets at most three sign-up, verification and reset emails an hour, from anywhere")
    void emailsToOneAddress() throws Exception {
        String email = "inbox-" + UUID.randomUUID() + "@example.test";
        for (String path : new String[] {"/reset_password_request", "/resend_email_token", "/reset_password_request"}) {
            mockMvc.perform(post("/api/v1/auth" + path)
                            .with(request -> {
                                request.setRemoteAddr(address());
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\"}".formatted(email.toUpperCase())))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/resend_email_token")
                        .with(request -> {
                            request.setRemoteAddr(address());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith("Too many emails sent to this address")));
    }

    @Test
    @DisplayName("a sign-up counts toward its address's emails once it sends one, not when refused before")
    void signUpEmails() throws Exception {
        String taken = createUser().getUsername();
        String email = "inbox-" + UUID.randomUUID() + "@example.test";
        for (int attempt = 0; attempt < 3; attempt++) {
            signUp(email, taken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("That username is already taken"));
        }
        // Each one replaces the account the one before made and never verified.
        for (int attempt = 0; attempt < 3; attempt++) {
            signUp(email, "fresh" + UUID.randomUUID().toString().substring(0, 8)).andExpect(status().isOk());
        }

        signUp(email, "fresh" + UUID.randomUUID().toString().substring(0, 8))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith("Too many emails sent to this address")));
    }

    private ResultActions signUp(String email, String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr(address());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "username": "%s", "firstName": "Rate", "lastName": "Limit",
                         "password": "Str0ng!password", "passwordConfirm": "Str0ng!password"}
                        """.formatted(email, username)));
    }

    @Test
    @DisplayName("a successful sign-in clears the failures before it")
    void successResets() throws Exception {
        User user = userWithPassword();
        String address = address();
        for (int attempt = 0; attempt < 4; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }
        login(address, user.getEmail(), PASSWORD).andExpect(status().isOk());

        for (int attempt = 0; attempt < 4; attempt++) {
            login(address, user.getEmail(), "Wrong!password1").andExpect(status().isUnauthorized());
        }
        login(address, user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an address can sign up at most five times an hour")
    void signUps() throws Exception {
        String address = address();
        for (int attempt = 0; attempt < 6; attempt++) {
            String handle = "limit" + UUID.randomUUID().toString().substring(0, 8);
            ResultActions result = mockMvc.perform(post("/api/v1/auth/register")
                    .with(request -> {
                        request.setRemoteAddr(address);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "%s@example.test", "username": "%s", "firstName": "Rate", "lastName": "Limit",
                             "password": "Str0ng!password", "passwordConfirm": "Str0ng!password"}
                            """.formatted(handle, handle)));
            if (attempt < 5) {
                result.andExpect(status().isOk());
            } else {
                result.andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.message").value(startsWith("Too many sign-ups from this network")));
            }
        }
    }

    private int waitingOnBuckets() {
        return jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
                        + " and query like 'SELECT state as state FROM rate_limit_buckets%'",
                Integer.class);
    }

    @Test
    @DisplayName("with every connection of the limiter busy, a sign-in is refused within seconds with a 503 to retry,"
            + " not a 500 half a minute later")
    void busyLimiter() throws Exception {
        String flooding = address();
        rateLimiter.consume(RateLimit.SIGN_UP, flooding);
        ExecutorService waiting = Executors.newFixedThreadPool(PostgresBucketStore.POOL_SIZE);
        try (Connection holder = dataSource.getConnection()) {
            // A lock held on one bucket, as by a slow request: every call for
            // that bucket waits on one of the limiter's connections.
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("select id from rate_limit_buckets where id = ? for update")) {
                lock.setString(1, store.id(RateLimit.SIGN_UP, flooding));
                lock.executeQuery().close();
            }
            for (int call = 0; call < PostgresBucketStore.POOL_SIZE; call++) {
                waiting.submit(() -> rateLimiter.consume(RateLimit.SIGN_UP, flooding));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (waitingOnBuckets() < PostgresBucketStore.POOL_SIZE && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(waitingOnBuckets()).isEqualTo(PostgresBucketStore.POOL_SIZE);

            long start = System.nanoTime();
            login(address(), "nobody@example.test", "Wrong!password1")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(jsonPath("$.message")
                            .value("Too many requests right now. Please try again in 5 seconds."));
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
            holder.rollback();
        } finally {
            waiting.shutdown();
            assertThat(waiting.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
