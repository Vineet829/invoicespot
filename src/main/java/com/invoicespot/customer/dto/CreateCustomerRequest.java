package com.invoicespot.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @Size(max = 255, message = "Customer names can be at most 255 characters") String name,
        @Email(message = "Enter a valid email address")
                @Size(max = 254, message = "Enter a valid email address")
                String email,
        @Size(max = 30, message = "Phone numbers can be at most 30 characters") String phoneNumber,
        @Size(max = 50, message = "VAT / TIN numbers can be at most 50 characters") String vatTinNo,
        @Size(max = 255, message = "Addresses can be at most 255 characters") String address,
        @Size(max = 255, message = "City names can be at most 255 characters") String city,
        @Size(max = 255, message = "Country names can be at most 255 characters") String country) {}
