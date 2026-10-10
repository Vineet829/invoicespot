package com.invoicespot.auth.dto;

import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Size(max = 254, message = "Enter a valid email address") String email,
        @Size(max = 128, message = "Passwords can be at most 128 characters") String password) {}
