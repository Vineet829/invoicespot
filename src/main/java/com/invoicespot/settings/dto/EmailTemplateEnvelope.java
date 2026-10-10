package com.invoicespot.settings.dto;

public record EmailTemplateEnvelope(boolean success, String message, EmailTemplateResponse template) {}
