package com.invoicespot.user.dto;

public record UpdatedProfileResponse(
        boolean success, String message, UserProfileResponse updatedProfile) {}
