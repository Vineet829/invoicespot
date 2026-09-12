package com.invoicespot.document.dto;

import com.invoicespot.document.PaymentMethod;
import java.time.Instant;

public record PaymentRecordDto(
        String paidBy,
        String datePaid,
        Double amountPaid,
        PaymentMethod paymentMethod,
        String additionalInfo,
        Instant createdAt,
        Instant updatedAt) {}
