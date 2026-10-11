package com.invoicespot.auth.dto;

import com.invoicespot.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") @Size(max = 128, message = "Enter your current password")
                String currentPassword,
        @NotBlank(message = "Enter a new password")
                @Size(max = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.TOO_LONG)
                String newPassword,
        @NotBlank(message = "Confirm the new password") String confirmPassword) {}
