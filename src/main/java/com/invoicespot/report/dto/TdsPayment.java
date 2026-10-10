package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TdsPayment(
        LocalDate datePaid,
        UUID customerId,
        String client,
        String pan,
        UUID documentId,
        String documentNumber,
        BigDecimal received,
        BigDecimal withheld) {}
