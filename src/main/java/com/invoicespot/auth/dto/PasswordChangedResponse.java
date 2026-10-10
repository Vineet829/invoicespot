package com.invoicespot.auth.dto;

public record PasswordChangedResponse(boolean success, String message, String accessToken) {}
