package com.invoicespot.document.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecurringRequest(
        String documentId,
        @NotBlank(message = "Choose how often the invoice repeats") String frequency,
        @NotNull(message = "Choose the date of the first invoice") LocalDate startDate,
        LocalDate endDate,
        @Min(value = 1, message = "Repeat from 1 to 120 times")
                @Max(value = 120, message = "Repeat from 1 to 120 times")
                Integer maxRuns,
        Boolean emailAutomatically) {}
