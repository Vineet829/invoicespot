package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringResponse(
        @JsonProperty("_id") UUID id,
        UUID documentId,
        String documentNumber,
        String clientName,
        BigDecimal total,
        String currency,
        String frequency,
        LocalDate startDate,
        LocalDate nextRun,
        LocalDate endDate,
        Integer maxRuns,
        int runsDone,
        boolean emailAutomatically,
        boolean paused,
        boolean finished,
        DocumentLink lastInvoice) {}
