package com.invoicespot.document.dto;

// The wa.me link to open, and whether a link that couldn't be shown again was
// replaced to make it.
public record WhatsAppShareResponse(boolean success, String url, boolean linkReplaced) {}
