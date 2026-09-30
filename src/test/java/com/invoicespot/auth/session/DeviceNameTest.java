package com.invoicespot.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeviceNameTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/129.0.0.0 Safari/537.36|Chrome on macOS",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0|Edge on Windows",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
                        + " Version/17.6 Mobile/15E148 Safari/604.1|Safari on iPhone",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/129.0.0.0 Mobile Safari/537.36|Chrome on Android",
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0|Firefox on Linux",
                "curl/8.7.1|Unknown device",
            })
    @DisplayName("reads the browser and system from a user agent")
    void names(String userAgent, String expected) {
        assertThat(DeviceName.of(userAgent)).isEqualTo(expected);
    }
}
