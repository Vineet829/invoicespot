package com.invoicespot.customer.dto;

public record UpdateCustomerRequest(
        String name,
        String email,
        String phoneNumber,
        Long vatTinNo,
        String address,
        String city,
        String country) {}
