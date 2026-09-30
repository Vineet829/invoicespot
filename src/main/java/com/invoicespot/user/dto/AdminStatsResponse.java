package com.invoicespot.user.dto;

public record AdminStatsResponse(
        boolean success,
        long accounts,
        long active,
        long admins,
        long unverified,
        long joinedLast30Days,
        long signedInLast30Days) {}
