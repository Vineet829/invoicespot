package com.invoicespot.user.dto;

import jakarta.validation.constraints.NotNull;

public record AdminRoleRequest(@NotNull(message = "Say whether the user should be an admin") Boolean admin) {}
