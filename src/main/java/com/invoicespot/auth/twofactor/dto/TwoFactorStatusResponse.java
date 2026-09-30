package com.invoicespot.auth.twofactor.dto;

import java.time.Instant;

public record TwoFactorStatusResponse(
        boolean success, boolean available, boolean enabled, Instant enabledAt, long recoveryCodesLeft) {}
