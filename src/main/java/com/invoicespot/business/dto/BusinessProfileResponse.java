package com.invoicespot.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import java.util.UUID;

public record BusinessProfileResponse(
        @JsonProperty("_id") UUID id,
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
        String taxId,
        BusinessRole role) {

    public static BusinessProfileResponse from(Business business, BusinessRole role) {
        return new BusinessProfileResponse(
                business.getId(),
                business.getName(),
                business.getLegalName(),
                business.getEmail(),
                business.getPhoneNumber(),
                business.getAddress(),
                business.getCity(),
                business.getState(),
                business.getPostalCode(),
                business.getCountry(),
                business.getLogo(),
                business.getTaxId(),
                role);
    }
}
