package com.invoicespot.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.user.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        @JsonProperty("_id") UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        String provider,
        boolean active,
        boolean isEmailVerified,
        Set<String> roles,
        Instant createdAt) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getProvider(),
                user.isActive(),
                user.isEmailVerified(),
                user.getRoles(),
                user.getCreatedAt());
    }
}
