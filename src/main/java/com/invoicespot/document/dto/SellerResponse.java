package com.invoicespot.document.dto;

import com.invoicespot.document.SellerSnapshot;

// The business as a document went out with it.
public record SellerResponse(
        String name,
        String legalName,
        String email,
        String phoneNumber,
        String address,
        String city,
        String state,
        String postalCode,
        String country,
        String logo,
        String taxId) {

    public static SellerResponse from(SellerSnapshot seller) {
        return seller == null
                ? null
                : new SellerResponse(
                        seller.getName(),
                        seller.getLegalName(),
                        seller.getEmail(),
                        seller.getPhoneNumber(),
                        seller.getAddress(),
                        seller.getCity(),
                        seller.getState(),
                        seller.getPostalCode(),
                        seller.getCountry(),
                        seller.getLogo(),
                        seller.getTaxId());
    }
}
