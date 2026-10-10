package com.invoicespot.document.dto;

// An email as it would go out with the subject and message being written,
// filled in from a real document and client.
public record EmailPreviewResponse(
        boolean success, String document, String client, String to, String subject, String message) {}
