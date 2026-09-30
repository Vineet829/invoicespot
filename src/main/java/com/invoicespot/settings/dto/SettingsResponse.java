package com.invoicespot.settings.dto;

import com.invoicespot.settings.BusinessSettings;
import java.math.BigDecimal;

public record SettingsResponse(
        String defaultCurrency,
        int paymentTermsDays,
        BigDecimal defaultTaxRate,
        String taxLabel,
        String defaultNotes,
        String defaultTerms,
        String emailMessage,
        String timeZone,
        String invoicePrefix,
        String quotationPrefix,
        String receiptPrefix,
        int nextInvoiceNumber,
        int nextQuotationNumber,
        int nextReceiptNumber,
        boolean saved) {

    public static SettingsResponse from(BusinessSettings settings) {
        return new SettingsResponse(
                settings.getDefaultCurrency(),
                settings.getPaymentTermsDays(),
                settings.getDefaultTaxRate().stripTrailingZeros(),
                settings.getTaxLabel(),
                settings.getDefaultNotes(),
                settings.getDefaultTerms(),
                settings.getEmailMessage(),
                settings.getTimeZone(),
                settings.getInvoicePrefix(),
                settings.getQuotationPrefix(),
                settings.getReceiptPrefix(),
                settings.getNextInvoiceNumber(),
                settings.getNextQuotationNumber(),
                settings.getNextReceiptNumber(),
                settings.getPkid() != null);
    }
}
