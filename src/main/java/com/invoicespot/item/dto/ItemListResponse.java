package com.invoicespot.item.dto;

import java.util.List;

public record ItemListResponse(boolean success, long totalItems, int numberOfPages, List<ItemResponse> items) {}
