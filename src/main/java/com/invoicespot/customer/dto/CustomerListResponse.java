package com.invoicespot.customer.dto;

import java.util.List;

public record CustomerListResponse(
        boolean success, long totalCustomers, int numberOfPages, List<CustomerResponse> myCustomers) {}
