package com.invoicespot.auth.dto;

public record RegisterRequest(
        String email,
        String username,
        String firstName,
        String lastName,
        String password,
        String passwordConfirm) {}
