package com.invoicespot.user.dto;

import java.util.Set;

public record UpdateProfileRequest(
        String password,
        String passwordConfirm,
        String email,
        Boolean isEmailVerified,
        String provider,
        Set<String> roles,
        String googleId,
        String username,
        String firstName,
        String lastName,
        String avatar,
        String businessName,
        String phoneNumber,
        String address,
        String city,
        String country) {}
