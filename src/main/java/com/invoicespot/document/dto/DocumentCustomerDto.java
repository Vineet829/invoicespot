package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;

public record DocumentCustomerDto(
        @Size(max = 255, message = "Customer names can be at most 255 characters") String name,
        @Size(max = 254, message = "Enter a valid email address") String email,
        @Size(max = 30, message = "Account numbers can be at most 30 characters") String accountNo,
        @Size(max = 255, message = "VAT / TIN numbers can be at most 255 characters") String vatTinNo,
        @Size(max = 255, message = "Addresses can be at most 255 characters") String address,
        @Size(max = 255, message = "City names can be at most 255 characters") String city,
        @Size(max = 255, message = "Country names can be at most 255 characters") String country,
        @Size(max = 30, message = "Phone numbers can be at most 30 characters") String phoneNumber,
        @Size(max = 20, message = "Enter a valid GSTIN, such as 27AAPFU0939F1ZV") String gstin,
        @Size(max = 2, message = "Choose the client's state from the list") String stateCode) {}
