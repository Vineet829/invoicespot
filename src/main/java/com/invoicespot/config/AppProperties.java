package com.invoicespot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cookie cookie) {

    public record Jwt(
            String accessSecret,
            String refreshSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {}

    public record Cookie(String refreshName, String path, String sameSite, boolean secure) {}
}
