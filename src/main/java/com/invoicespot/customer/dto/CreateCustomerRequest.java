package com.invoicespot.customer.dto;

public record CreateCustomerRequest(
        String name,
        String email,
        String phoneNumber,
        Long vatTinNo,
        String address,
        String city,
        String country) {}
