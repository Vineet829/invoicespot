package com.invoicespot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The key two-factor secrets are encrypted with: 32 random bytes, base64.
// Without it the app runs, but two-factor sign-in can't be turned on.
@ConfigurationProperties(prefix = "app.two-factor")
public record TwoFactorProperties(String encryptionKey) {}
