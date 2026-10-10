package com.invoicespot.business.dto;

import com.invoicespot.business.BusinessRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Choose a role") BusinessRole role) {}
