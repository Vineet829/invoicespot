package com.invoicespot.document.dto;

import java.time.Instant;
import java.util.List;

public record UpdateDocumentRequest(
        DocumentCustomerDto customer,
        String documentType,
        Instant dueDate,
        String additionalInfo,
        String termsConditions,
        String status,
        Double subTotal,
        Double salesTax,
        String rates,
        Double total,
        String currency,
        Double totalAmountReceived,
        List<BillingItemDto> billingItems) {}
