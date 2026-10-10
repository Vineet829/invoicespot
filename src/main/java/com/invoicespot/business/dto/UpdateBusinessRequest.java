package com.invoicespot.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Fields left out stay as they are; a blank one is cleared, except the name.
public record UpdateBusinessRequest(
        @Size(max = 255, message = "Business names can be at most 255 characters") String name,
        @Size(max = 255, message = "Legal names can be at most 255 characters") String legalName,
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email addresses can be at most 254 characters") String email,
        @Size(max = 30, message = "Phone numbers can be at most 30 characters") String phoneNumber,
        @Size(max = 255, message = "Addresses can be at most 255 characters") String address,
        @Size(max = 255, message = "City names can be at most 255 characters") String city,
        @Size(max = 255, message = "State names can be at most 255 characters") String state,
        @Size(max = 30, message = "Postal codes can be at most 30 characters") String postalCode,
        @Size(max = 255, message = "Country names can be at most 255 characters") String country,
        @Size(max = 500, message = "The logo address can be at most 500 characters") String logo,
        @Size(max = 50, message = "Tax IDs can be at most 50 characters") String taxId) {}
