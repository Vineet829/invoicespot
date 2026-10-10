package com.invoicespot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The one key the app seals secrets with: 32 random bytes, base64, from
// APP_ENCRYPTION_KEY. Without it the app runs, but nothing that needs it can
// be kept.
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String key) {}
