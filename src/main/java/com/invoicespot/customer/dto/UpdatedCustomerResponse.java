package com.invoicespot.customer.dto;

public record UpdatedCustomerResponse(
        boolean success, String message, CustomerResponse updatedCustomerInfo) {}
