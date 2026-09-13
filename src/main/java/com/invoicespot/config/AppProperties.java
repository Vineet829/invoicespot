package com.invoicespot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cookie cookie, Site site, Verification verification, Pdf pdf) {

    public record Jwt(
            String accessSecret,
            String refreshSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {}

    public record Cookie(String refreshName, String path, String sameSite, boolean secure) {}

    public record Site(String name, String clientUrl, String defaultFromEmail) {}

    public record Verification(Duration tokenTtl) {}

    public record Pdf(String storageDir) {}
}
