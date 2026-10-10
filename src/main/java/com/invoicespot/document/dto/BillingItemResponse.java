package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.invoicespot.document.BillingItem;
import com.invoicespot.document.DocumentTotals;
import java.math.BigDecimal;

public record BillingItemResponse(
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        String discount,
        BigDecimal lineTotal,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
        BigDecimal taxRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hsnSac) {

    // A quantity as it was typed: 2 rather than 2.0000, and 1.5 rather than
    // 1.5000.
    public static BillingItemResponse from(BillingItem item) {
        return new BillingItemResponse(
                item.getItemName(),
                item.getUnitPrice(),
                plain(item.getQuantity()),
                plain(item.getDiscount()).toPlainString(),
                DocumentTotals.lineTotal(item),
                item.getUnit(),
                plain(item.getTaxRate()),
                item.getHsnSac());
    }

    public static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = (value == null ? BigDecimal.ZERO : value).stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
