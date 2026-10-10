package com.invoicespot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    @DisplayName("the app's clock tells the time to the microsecond, as the database keeps it")
    void microseconds() {
        Clock clock = new ClockConfig().clock();

        for (int read = 0; read < 1_000; read++) {
            assertThat(clock.instant().getNano() % 1_000).isZero();
        }
    }
}
