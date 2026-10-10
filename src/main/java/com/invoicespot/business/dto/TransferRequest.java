package com.invoicespot.business.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferRequest(@NotBlank(message = "Choose who takes the business over") String userId) {}
