package com.invoicespot.audit.dto;

import java.util.List;

public record EventPageResponse<T>(boolean success, long total, int pages, int page, int size, List<T> events) {}
