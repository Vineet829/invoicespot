package com.invoicespot.document.dto;

public record EmailDraftResponse(boolean success, String to, String subject, String message) {}
