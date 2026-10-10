package com.invoicespot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorSignInRequest(
        @NotBlank(message = "Log in again") @Size(max = 2048, message = "Log in again") String challenge,
        @NotBlank(message = "Enter a code") @Size(max = 32, message = "Enter a code") String code) {}
