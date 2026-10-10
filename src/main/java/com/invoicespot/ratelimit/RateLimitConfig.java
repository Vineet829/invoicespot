package com.invoicespot.ratelimit;

import com.invoicespot.config.AppProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Rate limits live in the database by default, so every instance shares them.
// app.rate-limit.shared=false keeps them in each instance's memory instead.
@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.shared", havingValue = "true", matchIfMissing = true)
    PostgresBucketStore sharedBucketStore(JdbcConnectionDetails connection, Clock clock, AppProperties properties) {
        return new PostgresBucketStore(connection, clock, properties.jwt().accessSecret());
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.shared", havingValue = "false")
    LocalBucketStore localBucketStore(Clock clock) {
        return new LocalBucketStore(clock);
    }
}
