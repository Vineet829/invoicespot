package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;

// Why a report was dismissed, kept for the business only.
public record DismissClaimRequest(
        @Size(max = 200, message = "The reason can be at most 200 characters") String reason) {}
