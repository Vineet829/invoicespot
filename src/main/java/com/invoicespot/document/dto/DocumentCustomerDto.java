package com.invoicespot.document.dto;

public record DocumentCustomerDto(
        String name,
        String email,
        String accountNo,
        String vatTinNo,
        String address,
        String city,
        String country,
        String phoneNumber) {}
