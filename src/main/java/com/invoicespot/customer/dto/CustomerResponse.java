package com.invoicespot.customer.dto;

import com.invoicespot.customer.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID createdBy,
        String name,
        String email,
        String accountNo,
        Long vatTinNo,
        String address,
        String city,
        String country,
        String phoneNumber,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCreatedBy().getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getAccountNo(),
                customer.getVatTinNo(),
                customer.getAddress(),
                customer.getCity(),
                customer.getCountry(),
                customer.getPhoneNumber(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
