package com.invoicespot.auth.twofactor.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordRequest(@NotBlank(message = "Enter your password") String password) {}
