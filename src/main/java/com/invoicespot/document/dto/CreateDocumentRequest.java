package com.invoicespot.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CreateDocumentRequest(
        String customerId,
        @Valid DocumentCustomerDto customer,
        String documentType,
        Instant dueDate,
        @Size(max = 5000, message = "Additional info can be at most 5,000 characters") String additionalInfo,
        @Size(max = 5000, message = "Terms can be at most 5,000 characters") String termsConditions,
        String rates,
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter code such as USD") String currency,
        @NotEmpty(message = "Add at least one product or service")
                @Size(max = 200, message = "A document can have at most 200 items")
                List<@Valid BillingItemDto> billingItems,
        LocalDate issueDate,
        Boolean draft,
        String exchangeRate,
        String placeOfSupply,
        Boolean reverseCharge,
        String exportType,
        Boolean showPaymentDetails) {}
