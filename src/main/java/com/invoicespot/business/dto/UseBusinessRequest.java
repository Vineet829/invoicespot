package com.invoicespot.business.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UseBusinessRequest(@NotNull(message = "Choose a business") UUID businessId) {}
