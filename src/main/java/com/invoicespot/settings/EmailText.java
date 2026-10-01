package com.invoicespot.settings;

// An email's subject and message, before its placeholders are filled.
public record EmailText(String subject, String body) {}
