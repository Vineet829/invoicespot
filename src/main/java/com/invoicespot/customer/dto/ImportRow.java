package com.invoicespot.customer.dto;

import java.util.List;

public record ImportRow(
        int row,
        String name,
        String email,
        String phoneNumber,
        String vatTinNo,
        String address,
        String city,
        String country,
        List<String> problems) {

    public boolean ready() {
        return problems.isEmpty();
    }

    public CreateCustomerRequest request() {
        return new CreateCustomerRequest(name, email, phoneNumber, vatTinNo, address, city, country);
    }
}
