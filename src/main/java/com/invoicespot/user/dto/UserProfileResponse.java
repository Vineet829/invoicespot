package com.invoicespot.user.dto;

import com.invoicespot.user.User;
import java.time.Instant;

public record UserProfileResponse(
        String email,
        String username,
        String firstName,
        String lastName,
        boolean isEmailVerified,
        String provider,
        String avatar,
        String businessName,
        String phoneNumber,
        String address,
        String city,
        String country,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.isEmailVerified(),
                user.getProvider(),
                user.getAvatar(),
                user.getBusinessName(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getCity(),
                user.getCountry(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
