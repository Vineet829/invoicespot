package com.invoicespot.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.item.CatalogItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        @JsonProperty("_id") UUID id,
        String name,
        String description,
        BigDecimal unitPrice,
        Instant createdAt,
        Instant updatedAt) {

    public static ItemResponse from(CatalogItem item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
