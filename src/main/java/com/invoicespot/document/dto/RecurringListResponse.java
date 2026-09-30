package com.invoicespot.document.dto;

import java.util.List;

public record RecurringListResponse(boolean success, List<RecurringResponse> schedules) {}
