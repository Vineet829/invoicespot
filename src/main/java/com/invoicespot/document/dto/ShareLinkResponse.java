package com.invoicespot.document.dto;

import java.time.Instant;

// A new share link; `kept` says whether it can be shown again later.
public record ShareLinkResponse(boolean success, String url, Instant createdAt, boolean kept) {}
