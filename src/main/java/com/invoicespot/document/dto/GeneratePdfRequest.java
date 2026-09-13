package com.invoicespot.document.dto;

public record GeneratePdfRequest(
        PdfDocumentDto document,
        PdfProfileDto profile,
        Double balanceDue,
        String status,
        Double totalAmountReceived) {}
