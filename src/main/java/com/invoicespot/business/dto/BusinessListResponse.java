package com.invoicespot.business.dto;

import java.util.List;

public record BusinessListResponse(boolean success, List<BusinessSummaryResponse> businesses) {}
