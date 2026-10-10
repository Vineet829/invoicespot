package com.invoicespot.business.dto;

public record BusinessEnvelope(boolean success, String message, BusinessProfileResponse business) {}
