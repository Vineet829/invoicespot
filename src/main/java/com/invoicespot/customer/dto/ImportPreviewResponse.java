package com.invoicespot.customer.dto;

import java.util.List;

public record ImportPreviewResponse(boolean success, int total, int ready, List<ImportRow> rows) {}
