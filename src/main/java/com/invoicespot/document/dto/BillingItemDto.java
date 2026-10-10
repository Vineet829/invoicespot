package com.invoicespot.document.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillingItemDto(
        @NotBlank(message = "Every line needs a product or service name")
                @Size(max = 255, message = "Item names can be at most 255 characters")
                String itemName,
        @DecimalMin(value = "0", message = "Unit prices can't be negative")
                @Digits(
                        integer = 15,
                        fraction = 4,
                        message = "Unit prices can have at most 15 whole digits and 4 decimals")
                BigDecimal unitPrice,
        @DecimalMin(value = "0", message = "Quantities can't be negative")
                @DecimalMax(value = "1000000", message = "Quantities can be at most 1,000,000")
                @Digits(
                        integer = 7,
                        fraction = 4,
                        message = "Quantities can be at most 1,000,000, with up to 4 decimals")
                BigDecimal quantity,
        String discount,
        @Size(max = 20, message = "Units can be at most 20 characters") String unit,
        String taxRate,
        @Size(max = 12, message = "Enter an HSN or SAC code of 4 to 8 digits") String hsnSac) {}
