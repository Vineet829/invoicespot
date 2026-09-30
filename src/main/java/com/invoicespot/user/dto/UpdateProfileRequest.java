package com.invoicespot.user.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateProfileRequest(
        String password,
        String passwordConfirm,
        String email,
        Boolean isEmailVerified,
        String provider,
        Set<String> roles,
        String googleId,
        @Size(max = 60, message = "Usernames can be at most 60 characters") String username,
        @Size(max = 60, message = "First names can be at most 60 characters") String firstName,
        @Size(max = 60, message = "Last names can be at most 60 characters") String lastName) {}
