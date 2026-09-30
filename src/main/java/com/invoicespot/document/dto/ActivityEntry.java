package com.invoicespot.document.dto;

import java.time.Instant;

public record ActivityEntry(String type, String detail, Instant at) {}
