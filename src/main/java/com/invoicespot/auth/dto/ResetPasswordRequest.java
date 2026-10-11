package com.invoicespot.auth.dto;

import com.invoicespot.auth.PasswordPolicy;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Size(max = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.TOO_LONG) String password,
        @Size(max = 128, message = "Passwords can be at most 128 characters") String passwordConfirm,
        @Size(max = 64, message = "That reset link is not valid") String userId,
        @Size(max = 128, message = "That reset link is not valid") String emailToken) {}
