package com.invoicespot.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.common.Percentage;
import com.invoicespot.item.CatalogItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        @JsonProperty("_id") UUID id,
        String name,
        String description,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        String unit,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hsnSac,
        Instant createdAt,
        Instant updatedAt) {

    public static ItemResponse from(CatalogItem item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getUnitPrice(),
                Percentage.plain(item.getTaxRate()),
                item.getUnit(),
                item.getHsnSac(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
