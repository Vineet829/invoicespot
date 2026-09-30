package com.invoicespot.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateItemRequest(
        @Size(max = 255, message = "Item names can be at most 255 characters") String name,
        @Size(max = 1000, message = "Descriptions can be at most 1,000 characters") String description,
        @DecimalMin(value = "0", message = "Prices can't be negative")
                @Digits(integer = 15, fraction = 4, message = "Prices can have at most 4 decimals")
                BigDecimal unitPrice) {}
