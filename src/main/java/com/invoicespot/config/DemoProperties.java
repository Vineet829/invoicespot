package com.invoicespot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The shared demo account, and whether its sample data is cleared and added
// afresh at startup and every day, which only happens while it is seeded.
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(String email, boolean reset) {}
