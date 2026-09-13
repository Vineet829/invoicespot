package com.invoicespot.document.dto;

public record PdfProfileDto(
        String businessName,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String address,
        String city,
        String country,
        String avatar) {}
