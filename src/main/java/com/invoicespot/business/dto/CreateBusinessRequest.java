package com.invoicespot.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(
        @NotBlank(message = "A business needs a name")
        @Size(max = 255, message = "Business names can be at most 255 characters") String name) {}
