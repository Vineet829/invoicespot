package com.invoicespot.auth.dto;

public record ResetPasswordRequest(
        String password, String passwordConfirm, String userId, String emailToken) {}
