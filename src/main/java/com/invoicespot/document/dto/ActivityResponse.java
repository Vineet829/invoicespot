package com.invoicespot.document.dto;

import java.util.List;

public record ActivityResponse(boolean success, List<ActivityEntry> events) {}
