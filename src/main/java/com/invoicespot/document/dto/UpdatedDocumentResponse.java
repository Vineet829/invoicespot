package com.invoicespot.document.dto;

public record UpdatedDocumentResponse(
        boolean success, String message, DocumentResponse updatedDocument) {}
