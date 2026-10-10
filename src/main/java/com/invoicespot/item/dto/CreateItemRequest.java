package com.invoicespot.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateItemRequest(
        @Size(max = 255, message = "Item names can be at most 255 characters") String name,
        @Size(max = 1000, message = "Descriptions can be at most 1,000 characters") String description,
        @DecimalMin(value = "0", message = "Prices can't be negative")
                @Digits(integer = 15, fraction = 4, message = "Prices can have at most 15 whole digits and 4 decimals")
                BigDecimal unitPrice,
        String taxRate,
        @Size(max = 20, message = "Units can be at most 20 characters") String unit,
        @Size(max = 12, message = "Enter an HSN or SAC code of 4 to 8 digits") String hsnSac) {}
