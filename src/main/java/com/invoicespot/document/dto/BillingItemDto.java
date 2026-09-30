package com.invoicespot.document.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillingItemDto(
        @NotBlank(message = "Every line needs a product or service name")
                @Size(max = 255, message = "Item names can be at most 255 characters")
                String itemName,
        @DecimalMin(value = "0", message = "Unit prices can't be negative")
                @Digits(integer = 15, fraction = 4, message = "Unit prices can have at most 4 decimals")
                BigDecimal unitPrice,
        @PositiveOrZero(message = "Quantities can't be negative")
                @Max(value = 1_000_000, message = "Quantities can be at most 1,000,000")
                Integer quantity,
        String discount) {}
