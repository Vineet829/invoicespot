package com.invoicespot.business.dto;

import com.invoicespot.business.BusinessRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteRequest(
        @NotBlank(message = "Enter the email address to invite")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email addresses can be at most 254 characters") String email,
        @NotNull(message = "Choose a role") BusinessRole role) {}
