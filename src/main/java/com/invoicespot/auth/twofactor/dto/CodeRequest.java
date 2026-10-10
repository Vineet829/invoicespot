package com.invoicespot.auth.twofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeRequest(
        @NotBlank(message = "Enter a code") @Size(max = 32, message = "Enter a code") String code) {}
