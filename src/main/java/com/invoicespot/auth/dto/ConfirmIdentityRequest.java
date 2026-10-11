package com.invoicespot.auth.dto;

import jakarta.validation.constraints.Size;

// The account's password, and a code when two-factor sign-in is on, asked
// for again before something that can't be undone.
public record ConfirmIdentityRequest(
        @Size(max = 128, message = "Your password is wrong") String password,
        @Size(max = 20, message = "That code didn't work") String code) {}
