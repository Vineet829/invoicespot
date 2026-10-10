package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// One row of the payments list: a payment on an invoice, a receipt, or a
// refund, which is money going back and so below nothing. A payment or refund
// is named by its own id and a receipt by its document's; only a receipt has
// no method.
public record PaymentEntry(
        String kind,
        UUID id,
        LocalDate date,
        String client,
        Document document,
        String method,
        String note,
        BigDecimal amount,
        String currency) {

    public record Document(UUID id, String number, String type) {}
}
