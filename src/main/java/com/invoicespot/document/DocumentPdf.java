package com.invoicespot.document;

public record DocumentPdf(String fileName, byte[] content, DocumentEnvelope envelope) {}
