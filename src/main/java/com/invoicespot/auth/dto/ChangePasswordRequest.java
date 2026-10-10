package com.invoicespot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") @Size(max = 128, message = "Enter your current password")
                String currentPassword,
        @NotBlank(message = "Enter a new password") @Size(max = 128, message = "At most 128 characters")
                String newPassword,
        @NotBlank(message = "Confirm the new password") String confirmPassword) {}
