package com.invoicespot.customer.dto;

import java.util.List;

public record CustomerSummaryResponse(boolean success, List<CustomerTotals> totals) {}
