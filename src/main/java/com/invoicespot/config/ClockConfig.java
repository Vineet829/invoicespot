package com.invoicespot.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // To the microsecond, as PostgreSQL keeps times: on Linux the system clock
    // also has nanoseconds, so a time answered before it was saved would
    // differ from the same time read back.
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
