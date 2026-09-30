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
        @Size(max = 60, message = "Last names can be at most 60 characters") String lastName,
        @Size(max = 500, message = "The logo address can be at most 500 characters") String avatar,
        @Size(max = 255, message = "Business names can be at most 255 characters") String businessName,
        @Size(max = 30, message = "Phone numbers can be at most 30 characters") String phoneNumber,
        @Size(max = 255, message = "Addresses can be at most 255 characters") String address,
        @Size(max = 255, message = "City names can be at most 255 characters") String city,
        @Size(max = 255, message = "Country names can be at most 255 characters") String country) {}
