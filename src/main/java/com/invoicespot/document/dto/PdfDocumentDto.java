package com.invoicespot.document.dto;

import java.util.List;

public record PdfDocumentDto(
        String documentType,
        String documentNumber,
        String dueDate,
        String currency,
        Double subTotal,
        Double salesTax,
        Double total,
        DocumentCustomerDto customer,
        List<BillingItemDto> billingItems) {}
