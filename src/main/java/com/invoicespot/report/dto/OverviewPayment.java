package com.invoicespot.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OverviewPayment(
        @JsonProperty("_id") UUID id,
        String paidBy,
        LocalDate datePaid,
        String paymentMethod,
        String additionalInfo,
        BigDecimal amountPaid,
        Document document) {

    public record Document(
            @JsonProperty("_id") UUID id,
            String documentType,
            String documentNumber,
            BigDecimal total,
            String status,
            Instant dueDate) {}
}
