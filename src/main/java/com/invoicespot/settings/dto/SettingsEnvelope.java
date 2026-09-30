package com.invoicespot.settings.dto;

public record SettingsEnvelope(boolean success, String message, SettingsResponse settings) {}
