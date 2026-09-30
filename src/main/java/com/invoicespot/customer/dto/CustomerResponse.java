package com.invoicespot.customer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.customer.Customer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerResponse(
        @JsonProperty("_id") UUID id,
        UUID createdBy,
        String name,
        String email,
        String accountNo,
        String vatTinNo,
        String address,
        String city,
        String country,
        String phoneNumber,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long documentCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CustomerBalance> balances) {

    public static CustomerResponse from(Customer customer) {
        return from(customer, null, null);
    }

    public static CustomerResponse from(Customer customer, Long documentCount, List<CustomerBalance> balances) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCreatedBy() == null ? null : customer.getCreatedBy().getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getAccountNo(),
                customer.getVatTinNo(),
                customer.getAddress(),
                customer.getCity(),
                customer.getCountry(),
                customer.getPhoneNumber(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                documentCount,
                balances);
    }
}
