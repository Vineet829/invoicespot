package com.invoicespot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email(message = "Enter a valid email address")
                @Size(max = 254, message = "Enter a valid email address")
                String email,
        @Size(max = 60, message = "Usernames can be at most 60 characters") String username,
        @Size(max = 60, message = "First names can be at most 60 characters") String firstName,
        @Size(max = 60, message = "Last names can be at most 60 characters") String lastName,
        @Size(max = 128, message = "Passwords can be at most 128 characters") String password,
        @Size(max = 128, message = "Passwords can be at most 128 characters") String passwordConfirm) {}
