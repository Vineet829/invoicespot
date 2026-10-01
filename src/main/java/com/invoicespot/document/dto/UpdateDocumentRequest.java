package com.invoicespot.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record UpdateDocumentRequest(
        String customerId,
        @Valid DocumentCustomerDto customer,
        String documentType,
        String dueDate,
        @Size(max = 5000, message = "Additional info can be at most 5,000 characters") String additionalInfo,
        @Size(max = 5000, message = "Terms can be at most 5,000 characters") String termsConditions,
        String rates,
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter code such as USD") String currency,
        @Size(min = 1, max = 200, message = "A document needs 1 to 200 products or services")
                List<@Valid BillingItemDto> billingItems,
        LocalDate issueDate,
        String exchangeRate,
        @Size(max = 500, message = "The reason can be at most 500 characters") String creditReason,
        String placeOfSupply,
        Boolean reverseCharge,
        String exportType,
        Boolean showPaymentDetails) {}
