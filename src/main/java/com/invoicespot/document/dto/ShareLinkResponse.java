package com.invoicespot.document.dto;

import java.time.Instant;

public record ShareLinkResponse(boolean success, String url, Instant createdAt) {}
