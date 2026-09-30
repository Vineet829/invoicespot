package com.invoicespot.auth.dto;

import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Size(max = 128, message = "Passwords can be at most 128 characters") String password,
        @Size(max = 128, message = "Passwords can be at most 128 characters") String passwordConfirm,
        @Size(max = 64, message = "That reset link is not valid") String userId,
        @Size(max = 128, message = "That reset link is not valid") String emailToken) {}
